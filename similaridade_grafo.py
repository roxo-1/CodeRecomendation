"""
Cálculo de similaridade entre nós de um grafo não dirigido e ponderado.

Contexto:
  - Cada NÓ do grafo é uma linguagem de programação.
  - O PESO da aresta entre dois nós é calculado a partir da similaridade
    dos atributos das linguagens (paradigmas, tipagem, influência,
    aplicação, sintática, semântica), lidos da planilha
    'codeRecomendation_teste1.xlsx', aba 'esse aqui'.
  - O grafo resultante segue a mesma lógica do TGrafoNDPeso (Java):
    matriz de adjacência simétrica, sem aresta = infinito.
  - O cálculo é feito para TODOS os pares de linguagens da planilha
    (grafo completo), não apenas dois nós isolados.

Método de similaridade = combinação ponderada de:
  1) Jaccard  -> campos categóricos / multi-valorados
                 (Paradigmas, Influência, Tipagem forte ou fraco,
                  Tipagem dinamico ou estático)
                 Colunas em que os DOIS lados estão vazios são
                 ignoradas na média (célula vazia = "não sei",
                 não = "são iguais").
  2) TF-IDF + cosseno -> campos de texto livre
                 (Aplicação, Sintática, Semantica)
                 O TF-IDF é ajustado UMA VEZ para a planilha inteira
                 (não par a par), com stopwords em português removidas,
                 para que o peso de cada palavra reflita a raridade
                 dela no conjunto real de linguagens.

similaridade_final = W_JACCARD * media_jaccard + W_COSSENO * cosseno_texto

Linhas marcadas na coluna 'excluidas' são ignoradas por padrão
(veja INCLUIR_EXCLUIDAS abaixo).
"""

import re
import math
import unicodedata
import itertools
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

# ----------------------------------------------------------------------
# Configuração
# ----------------------------------------------------------------------

# Ajuste este caminho para onde a planilha está no seu ambiente.
CAMINHO_PLANILHA = "codeRecomendation_teste1.xlsx"
ABA = "esse aqui"

# Se False (padrão), ignora as linguagens marcadas na coluna 'excluidas'.
INCLUIR_EXCLUIDAS = False

COLUNAS_CATEGORICAS = [
    "Paradigmas",
    "Tipagem forte ou fraco",
    "Tipagem dinamico ou estático",
    "Influência",
]
COLUNAS_TEXTO = ["Aplicação", "Sintática", "Semantica"]

# Pesos da combinação (somam 1.0)
W_JACCARD = 0.5
W_COSSENO = 0.5

# Stopwords em português (conectores/artigos/preposições sem carga
# semântica própria) — removidas antes do TF-IDF para que a similaridade
# não seja inflada por palavras que aparecem em praticamente todo texto.
STOPWORDS_PT = {
    "a", "o", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da",
    "dos", "das", "em", "no", "na", "nos", "nas", "por", "para", "com",
    "sem", "sob", "sobre", "entre", "e", "ou", "mas", "que", "se", "ao",
    "aos", "à", "às", "é", "são", "foi", "ser", "sendo", "seu", "sua",
    "seus", "suas", "este", "esta", "isto", "esse", "essa", "isso",
    "muito", "muitos", "muitas", "mais", "menos", "como", "já", "não",
    "também", "onde", "quando", "quanto", "cada", "outro", "outra",
    "outros", "outras", "tem", "têm", "há", "num", "numa",
}

# Onde salvar os resultados
CSV_MATRIZ = "matriz_similaridade.csv"
CSV_ARESTAS = "lista_arestas.csv"


# ----------------------------------------------------------------------
# Normalização de nomes de coluna (compatibilidade entre abas)
# ----------------------------------------------------------------------

def _normaliza_nome(s: str) -> str:
    """minúsculas, sem acento, sem espaços extras — para comparar nomes
    de coluna que variam entre abas (ex.: 'Tipagem Forte ou Fraco' vs
    'Tipagem forte ou fraco')."""
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"\s+", " ", s).strip().lower()


_COLUNAS_CANONICAS = [
    "Linguagens escolhidas", "excluidas", "Motivo", "Paradigmas",
    "Tipagem forte ou fraco", "Tipagem dinamico ou estático",
    "Influência", "Aplicação", "Sintática", "Semantica", "Documentação",
]
_MAPA_NORMALIZADO = {_normaliza_nome(c): c for c in _COLUNAS_CANONICAS}
# Variantes/typos conhecidos entre abas (ex.: "...ou Estática" vs
# "...ou estático" — não é só acento/maiúscula, é a palavra terminando
# em "a" numa aba e em "o" noutra):
_MAPA_NORMALIZADO[_normaliza_nome("Tipagem Dinâmico ou Estática")] = "Tipagem dinamico ou estático"


# ----------------------------------------------------------------------
# Pré-processamento
# ----------------------------------------------------------------------

def _vazio(valor) -> bool:
    return valor is None or (isinstance(valor, float) and math.isnan(valor)) or str(valor).strip() == ""


def tokeniza_categorico(valor) -> set:
    """Transforma um campo categórico/multi-valorado em um conjunto de tokens.
    Ex: 'programação funcional,\\nprogramação imperativa,' ->
        {'programacao funcional', 'programacao imperativa'}
    Retorna conjunto vazio se o valor estiver vazio/ausente."""
    if _vazio(valor):
        return set()
    texto = str(valor).lower()
    texto = texto.replace("\n", ",")
    partes = re.split(r"[,;/]", texto)
    tokens = {p.strip() for p in partes if p.strip()}
    return tokens


def limpa_texto(valor) -> str:
    """Normaliza um campo de texto livre para uso no TF-IDF."""
    if _vazio(valor):
        return ""
    texto = str(valor).lower()
    texto = texto.replace("\n", " ")
    texto = re.sub(r"[^\w\sáéíóúâêôãõçü]", " ", texto)
    texto = re.sub(r"\s+", " ", texto).strip()
    return texto


# ----------------------------------------------------------------------
# Similaridade categórica (Jaccard, com tratamento correto de "vazio")
# ----------------------------------------------------------------------

def jaccard_ou_none(a: set, b: set):
    """Retorna None quando os dois lados estão vazios (= 'sem dado',
    não deve contar como se fossem iguais). Retorna 0.0 quando só um
    lado tem dado (divergência real: um descreve algo que o outro não
    documentou). Caso contrário, Jaccard normal."""
    if not a and not b:
        return None
    if not a or not b:
        return 0.0
    inter = len(a & b)
    uniao = len(a | b)
    return inter / uniao if uniao else 0.0


def similaridade_categorica(linha_a: pd.Series, linha_b: pd.Series) -> float:
    """Média do Jaccard nas colunas categóricas, ignorando colunas em
    que AMBOS os lados estão vazios (não têm informação nenhuma)."""
    scores = []
    for col in COLUNAS_CATEGORICAS:
        set_a = tokeniza_categorico(linha_a.get(col))
        set_b = tokeniza_categorico(linha_b.get(col))
        s = jaccard_ou_none(set_a, set_b)
        if s is not None:
            scores.append(s)
    if not scores:
        # nenhuma das colunas categóricas tinha dado dos dois lados
        return 0.0
    return sum(scores) / len(scores)


# ----------------------------------------------------------------------
# Similaridade textual (TF-IDF ajustado UMA VEZ para todo o corpus)
# ----------------------------------------------------------------------

def monta_matriz_tfidf(df: pd.DataFrame):
    """Ajusta o TF-IDF sobre os textos de TODAS as linguagens de uma vez
    (não par a par) e já calcula a matriz de similaridade de cosseno
    completa (N x N) — muito mais rápido e estatisticamente correto,
    pois o IDF passa a refletir a raridade real de cada palavra no
    conjunto inteiro de linguagens."""
    textos = [
        " ".join(limpa_texto(row.get(col)) for col in COLUNAS_TEXTO)
        for _, row in df.iterrows()
    ]
    vetor = TfidfVectorizer(stop_words=list(STOPWORDS_PT))
    matriz_tfidf = vetor.fit_transform(textos)
    return cosine_similarity(matriz_tfidf)  # matriz NxN, sim[i][j]


def similaridade_no(linha_a: pd.Series, linha_b: pd.Series, sim_texto: float) -> dict:
    """Combina a similaridade categórica (calculada aqui) com a
    similaridade textual (já pré-calculada em `sim_texto`, vinda da
    matriz de cosseno do corpus inteiro)."""
    sim_jaccard = similaridade_categorica(linha_a, linha_b)
    sim_final = W_JACCARD * sim_jaccard + W_COSSENO * sim_texto

    return {
        "no_a": linha_a["Linguagens escolhidas"],
        "no_b": linha_b["Linguagens escolhidas"],
        "similaridade_jaccard": round(sim_jaccard, 4),
        "similaridade_cosseno": round(sim_texto, 4),
        "similaridade_final": round(sim_final, 4),
    }


# ----------------------------------------------------------------------
# Grafo Não Dirigido e Ponderado (equivalente Python do TGrafoNDPeso)
# ----------------------------------------------------------------------

class GrafoNDPeso:
    """Versão Python da classe Java TGrafoNDPeso: matriz de adjacência
    simétrica, com peso nas arestas. Ausência de aresta = infinito."""

    def __init__(self, n: int, nomes: list[str] | None = None):
        self.n = n
        self.m = 0
        self.adj = [[math.inf] * n for _ in range(n)]
        self.nomes = nomes if nomes is not None else [str(i) for i in range(n)]

    def insere_and(self, v: int, w: int, peso: float) -> None:
        if self.adj[v][w] == math.inf:
            self.adj[v][w] = peso
            self.adj[w][v] = peso
            self.m += 1

    def remove_and(self, v: int, w: int) -> None:
        if self.adj[v][w] != math.inf:
            self.adj[v][w] = math.inf
            self.adj[w][v] = math.inf
            self.m -= 1

    def grau_nd(self, v: int) -> int:
        grau = sum(1 for i in range(self.n) if self.adj[v][i] != math.inf)
        print(f"Grau do vértice {self.nomes[v]} é {grau}")
        return grau

    def show_nd(self) -> None:
        print(f"n: {self.n}")
        print(f"m: {self.m}")
        for i in range(self.n):
            for w in range(self.n):
                peso = self.adj[i][w]
                txt = f"{peso:.4f}" if peso != math.inf else "inf"
                print(f"Adj[{self.nomes[i]},{self.nomes[w]}]={txt} ", end="")
            print()
        print("\nfim da impressao do grafo.")

    def peso_aresta(self, nome_a: str, nome_b: str) -> float:
        i = self.nomes.index(nome_a)
        j = self.nomes.index(nome_b)
        return self.adj[i][j]

    def para_dataframe(self) -> pd.DataFrame:
        return pd.DataFrame(self.adj, index=self.nomes, columns=self.nomes)

    def lista_arestas(self) -> pd.DataFrame:
        linhas = []
        for i in range(self.n):
            for j in range(i + 1, self.n):
                if self.adj[i][j] != math.inf:
                    linhas.append((self.nomes[i], self.nomes[j], self.adj[i][j]))
        return (
            pd.DataFrame(linhas, columns=["no_a", "no_b", "peso"])
            .sort_values("peso", ascending=False)
            .reset_index(drop=True)
        )


# ----------------------------------------------------------------------
# Construção do grafo completo (todas as linguagens da aba)
# ----------------------------------------------------------------------

def carrega_dados() -> pd.DataFrame:
    """Lê a planilha, renomeia as colunas para o nome canônico (caso a
    aba use maiúsculas/acentos diferentes) e, por padrão, remove as
    linguagens marcadas como 'excluidas' (a menos que
    INCLUIR_EXCLUIDAS = True)."""
    df = pd.read_excel(CAMINHO_PLANILHA, sheet_name=ABA)

    renomeio = {
        col: _MAPA_NORMALIZADO[_normaliza_nome(col)]
        for col in df.columns
        if _normaliza_nome(col) in _MAPA_NORMALIZADO
    }
    df = df.rename(columns=renomeio)

    if not INCLUIR_EXCLUIDAS and "excluidas" in df.columns:
        antes = len(df)
        df = df[df["excluidas"].isna()].reset_index(drop=True)
        removidas = antes - len(df)
        if removidas:
            print(f"[info] {removidas} linguagem(ns) marcada(s) como excluída(s) foram ignoradas.")

    return df


def constroi_grafo_completo(df: pd.DataFrame) -> GrafoNDPeso:
    """Recebe um DataFrame (uma aba da planilha) e monta o grafo completo:
    um nó para cada linha e uma aresta entre CADA par de nós, com peso
    igual à similaridade calculada por `similaridade_no`."""
    nomes = df["Linguagens escolhidas"].tolist()
    g = GrafoNDPeso(n=len(nomes), nomes=nomes)

    # TF-IDF/cosseno calculado UMA VEZ para o corpus inteiro
    matriz_cosseno = monta_matriz_tfidf(df)

    for i, j in itertools.combinations(range(len(df)), 2):
        resultado = similaridade_no(df.iloc[i], df.iloc[j], matriz_cosseno[i][j])
        g.insere_and(i, j, resultado["similaridade_final"])

    return g


# ----------------------------------------------------------------------
# Execução principal
# ----------------------------------------------------------------------

def main():
    df = carrega_dados()

    if len(df) < 2:
        raise ValueError(f"A aba '{ABA}' precisa ter ao menos 2 linhas (nós) após os filtros.")

    print(f"Calculando similaridade para {len(df)} linguagens "
          f"({len(df) * (len(df) - 1) // 2} pares)...\n")

    g = constroi_grafo_completo(df)

    print(f"Grafo montado com {g.n} nós e {g.m} arestas.\n")

    tabela_arestas = g.lista_arestas()
    print("=== Top 10 pares MAIS similares ===")
    print(tabela_arestas.head(10).to_string(index=False))
    print("\n=== Top 10 pares MENOS similares ===")
    print(tabela_arestas.tail(10).to_string(index=False))

    tabela_arestas.to_csv(CSV_ARESTAS, index=False)
    g.para_dataframe().to_csv(CSV_MATRIZ)
    print(f"\nLista de arestas exportada para: {CSV_ARESTAS}")
    print(f"Matriz de adjacência completa exportada para: {CSV_MATRIZ}")


if __name__ == "__main__":
    main()
