"""
Code Recomendation - Teoria dos Grafos (Mackenzie)

Integrantes:
  Carolina Lee                       - RA 10440304 - 10440304@mackenzista.com.br
  Pedro Casas Pequeno Junior         - RA 10437031 - 10437031@mackenzista.com.br
  Pedro Gabriel Guimarães Fernandes  - RA 10437465 - 10437465@mackenzista.com.br

Síntese:
  Calcula a similaridade entre linguagens de programação (vértices) a partir
  da planilha 'codeRecomendation_preenchido.xlsx' (aba 'esse aqui') e monta
  um grafo NÃO DIRIGIDO e PONDERADO, na mesma lógica do TGrafoNDPeso (Java):
  matriz de adjacência simétrica, sem aresta = infinito.

  O PESO da aresta é uma DISTÂNCIA:

        distancia = 1 - similaridade

  Assim, quanto mais parecidas duas linguagens, MENOR o peso da aresta, e os
  algoritmos de custo mínimo (Dijkstra, Floyd, Prim) passam a encontrar
  as linguagens mais próximas umas das outras.

  Similaridade = combinação ponderada de:
    1) Jaccard  -> campos categóricos / multi-valorados
                   (Paradigmas, Influência, Tipagem forte ou fraco,
                    Tipagem dinamico ou estático). Os termos são
                   normalizados antes (sem acento, sem o prefixo
                   "programação", gênero unificado, sinônimos), para que
                   "Orientada a objetos" e "orientado a objetos" contem
                   como o MESMO paradigma.
    2) TF-IDF + cosseno -> campos de texto livre
                   (Aplicação, Sintática, Semantica), ajustado UMA VEZ para
                   a planilha inteira, com stopwords em português removidas.

  similaridade = W_JACCARD * media_jaccard + W_COSSENO * cosseno_texto

  Nota de corte: o grafo completo tem n(n-1)/2 arestas. Só são mantidas as
  arestas com similaridade >= corte (ou seja, distancia <= 1 - corte).
  O corte é calculado automaticamente (veja MODO_CORTE).

Arquivos gerados:
  lista_arestas.csv     - arestas do grafo (com corte): índices, nomes,
                          similaridade e distância (peso)
  matriz_distancias.csv - matriz de adjacência do grafo (com corte), inf = sem aresta
  grafo.txt             - lista de arestas no formato do Graph Online
                          (graphonline.top -> "Criar grafo por lista de arestas")
  grafo_java.txt        - arquivo lido pelo TGrafoNDPeso.java
  dados_grafo.js        - dados usados pela página web (index.html)

Histórico de alterações:
  05/11/2026 - Pedro Gabriel Guimarães Fernandes - peso passa a ser
               distância (1 - similaridade); normalização dos termos
               categóricos; corte automático (grafo conexo); exportação de
               grafo.txt (Graph Online), grafo_java.txt e dados_grafo.js.
"""

import re
import json
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
CAMINHO_PLANILHA = "codeRecomendation_preenchido.xlsx"
ABA = "esse aqui"

# Se False (padrão), ignora as linguagens marcadas na coluna 'excluidas'
# (se a coluna existir na aba).
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

# Como escolher a nota de corte (similaridade mínima para existir aresta):
#   "conexo"      -> o MAIOR corte que ainda deixa o grafo CONEXO (todo
#                    vértice alcança todos os outros). É o padrão, porque
#                    Dijkstra, Floyd e Prim precisam do grafo conexo para
#                    recomendar a partir de qualquer linguagem.
#   "grau_minimo" -> o MAIOR corte em que todo vértice tem pelo menos 1
#                    aresta (critério usado na Parte 2 — não garante
#                    grafo conexo).
#   "fixo"        -> usa CORTE_FIXO.
#   "nenhum"      -> grafo completo (todas as arestas).
MODO_CORTE = "conexo"
CORTE_FIXO = 0.2837

# Casas decimais dos pesos exportados
CASAS = 4

# Stopwords em português (conectores/artigos/preposições sem carga
# semântica própria) — removidas antes do TF-IDF.
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
CSV_ARESTAS = "lista_arestas.csv"
CSV_MATRIZ = "matriz_distancias.csv"
TXT_GRAPHONLINE = "grafo.txt"
TXT_JAVA = "grafo_java.txt"
JS_PAGINA = "dados_grafo.js"


# ----------------------------------------------------------------------
# Normalização de nomes de coluna (compatibilidade entre abas)
# ----------------------------------------------------------------------

def sem_acento(s: str) -> str:
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode("ascii")


def _normaliza_nome(s: str) -> str:
    """minúsculas, sem acento, sem espaços extras — para comparar nomes
    de coluna que variam entre abas."""
    return re.sub(r"\s+", " ", sem_acento(str(s))).strip().lower()


_COLUNAS_CANONICAS = [
    "Linguagens escolhidas", "excluidas", "Motivo", "Paradigmas",
    "Tipagem forte ou fraco", "Tipagem dinamico ou estático",
    "Influência", "Aplicação", "Sintática", "Semantica", "Documentação",
]
_MAPA_NORMALIZADO = {_normaliza_nome(c): c for c in _COLUNAS_CANONICAS}
_MAPA_NORMALIZADO[_normaliza_nome("Tipagem Dinâmico ou Estática")] = "Tipagem dinamico ou estático"


# ----------------------------------------------------------------------
# Pré-processamento dos campos categóricos
# ----------------------------------------------------------------------

def _vazio(valor) -> bool:
    return valor is None or (isinstance(valor, float) and math.isnan(valor)) or str(valor).strip() == ""


# Sinônimos de paradigma (já sem acento e sem o prefixo "programacao").
# Unifica gênero/número ("imperativa"/"imperativo"), erros de digitação
# ("imterativa") e conceitos equivalentes ("baseada em classe" -> OO).
_SINONIMOS_PARADIGMA = {
    "imperativa": "imperativo", "imperativo": "imperativo", "imterativa": "imperativo",
    "funcional": "funcional",
    "procedural": "procedural",
    "concorrente": "concorrente", "process-oriented programming": "concorrente",
    "paralela (simt)": "concorrente",
    "declarativa": "declarativo", "declarativo": "declarativo",
    "estruturada": "estruturado", "estruturado": "estruturado",
    "nao estruturada": "nao estruturado",
    "reflexao": "reflexao", "reflexivo": "reflexao", "reflexiva": "reflexao",
    "orientado a objetos": "orientado a objetos", "orientada a objetos": "orientado a objetos",
    "orientados a objetos": "orientado a objetos", "baseada em classe": "orientado a objetos",
    "heranca": "orientado a objetos", "polimorfismo": "orientado a objetos",
    "orientada a eventos": "orientado a eventos", "orientado a eventos": "orientado a eventos",
    "generica": "generico", "generico": "generico",
    "multi-paradigma": "multiparadigma",
    "metaprogramacao": "metaprogramacao", "macro metaprogramming": "metaprogramacao",
    "meta": "metaprogramacao",
    "logica": "logico",
    "de matriz": "array", "orientado a arrays": "array",
    "baseada em prototipos": "prototipos", "orientada a prototipos": "prototipos",
    "de fluxo de dados (dataflow)": "fluxo de dados",
    "orientada a linguagem": "orientado a linguagem",
    "orientada a agentes": "orientado a agentes",
    "protocol-oriented": "orientado a protocolos",
}

_COMPOSTOS_PARADIGMA = {"metaprogramacao reflexao": ["metaprogramacao", "reflexao"]}

_SINONIMOS_DIN_EST = {"dinamico": "dinamico", "dinamica": "dinamico",
                      "estatico": "estatico", "estatica": "estatico"}
_SINONIMOS_FORTE_FRACO = {"forte": "forte", "fraco": "fraco", "fraca": "fraco",
                          "sem tipagem": "sem tipagem"}


def _partes(valor, separa_programacao=False) -> list:
    """Quebra uma célula multi-valorada em termos. Separadores: vírgula,
    ponto e vírgula, quebra de linha e ' / ' (com espaços — assim 'PL/I'
    e 'C/C++' não são quebrados)."""
    if _vazio(valor):
        return []
    texto = sem_acento(str(valor)).lower()
    padrao = r"[,;\n]|\s/\s"
    if separa_programacao:
        # células como "programação procedural programação funcional"
        padrao += r"|(?=\bprogramacao\b)"
    partes = re.split(padrao, texto)
    return [p.strip(" .\t") for p in partes if p and p.strip(" .\t")]


def tokeniza_categorico(valor, coluna: str) -> set:
    """Transforma um campo categórico em um conjunto de termos normalizados."""
    if coluna == "Paradigmas":
        tokens = set()
        for p in _partes(valor, separa_programacao=True):
            p = re.sub(r"^programacao\s+", "", p).strip()
            p = re.sub(r"^orientada a$", "", p)
            # célula sem separador entre dois termos (ex.: "metaprogramação reflexão")
            for q in _COMPOSTOS_PARADIGMA.get(p, [p]):
                if q:
                    tokens.add(_SINONIMOS_PARADIGMA.get(q, q))
        return tokens
    if coluna == "Tipagem dinamico ou estático":
        # termos que não pertencem a esta coluna (ex.: "Forte") são ignorados
        return {_SINONIMOS_DIN_EST[p] for p in _partes(valor) if p in _SINONIMOS_DIN_EST}
    if coluna == "Tipagem forte ou fraco":
        return {_SINONIMOS_FORTE_FRACO.get(p, p) for p in _partes(valor)}
    # Influência
    return set(_partes(valor))


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
    """None quando os dois lados estão vazios ('sem dado'); 0.0 quando só
    um lado tem dado; senão, Jaccard normal |A∩B| / |A∪B|."""
    if not a and not b:
        return None
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def similaridade_categorica(tokens_a: dict, tokens_b: dict) -> float:
    """Média do Jaccard nas colunas categóricas, ignorando colunas em
    que AMBOS os lados estão vazios."""
    scores = []
    for col in COLUNAS_CATEGORICAS:
        s = jaccard_ou_none(tokens_a[col], tokens_b[col])
        if s is not None:
            scores.append(s)
    return sum(scores) / len(scores) if scores else 0.0


# ----------------------------------------------------------------------
# Similaridade textual (TF-IDF ajustado UMA VEZ para todo o corpus)
# ----------------------------------------------------------------------

def monta_matriz_cosseno(df: pd.DataFrame):
    textos = [
        " ".join(limpa_texto(row.get(col)) for col in COLUNAS_TEXTO)
        for _, row in df.iterrows()
    ]
    vetor = TfidfVectorizer(stop_words=list(STOPWORDS_PT))
    matriz_tfidf = vetor.fit_transform(textos)
    return cosine_similarity(matriz_tfidf)  # matriz NxN


def matriz_similaridade(df: pd.DataFrame, tokens: list) -> list:
    """Matriz N x N de similaridade final (diagonal = 1)."""
    n = len(df)
    cos = monta_matriz_cosseno(df)
    sim = [[1.0] * n for _ in range(n)]
    for i, j in itertools.combinations(range(n), 2):
        s_jac = similaridade_categorica(tokens[i], tokens[j])
        s = W_JACCARD * s_jac + W_COSSENO * float(cos[i][j])
        sim[i][j] = sim[j][i] = round(s, CASAS)
    return sim


# ----------------------------------------------------------------------
# Grafo Não Dirigido e Ponderado (equivalente Python do TGrafoNDPeso)
# ----------------------------------------------------------------------

class GrafoNDPeso:
    """Versão Python da classe Java TGrafoNDPeso: matriz de adjacência
    simétrica, peso = distância. Ausência de aresta = infinito."""

    def __init__(self, n: int, nomes: list | None = None):
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
        return sum(1 for i in range(self.n) if self.adj[v][i] != math.inf)

    def conexo(self) -> bool:
        """Busca em profundidade a partir do vértice 0."""
        if self.n == 0:
            return True
        visitado = [False] * self.n
        pilha = [0]
        visitado[0] = True
        while pilha:
            v = pilha.pop()
            for w in range(self.n):
                if self.adj[v][w] != math.inf and not visitado[w]:
                    visitado[w] = True
                    pilha.append(w)
        return all(visitado)

    def lista_arestas(self) -> list:
        arestas = []
        for i in range(self.n):
            for j in range(i + 1, self.n):
                if self.adj[i][j] != math.inf:
                    arestas.append((i, j, self.adj[i][j]))
        return arestas


# ----------------------------------------------------------------------
# Nota de corte
# ----------------------------------------------------------------------

def corte_grau_minimo(sim: list) -> float:
    """Maior corte tal que todo vértice tem ao menos 1 aresta:
    min_v ( max_w sim[v][w] )."""
    n = len(sim)
    return min(max(sim[v][w] for w in range(n) if w != v) for v in range(n))


def corte_conexo(sim: list) -> float:
    """Maior corte que mantém o grafo conexo. É a MENOR aresta da árvore
    geradora de MÁXIMA similaridade (= maior aresta da árvore geradora
    MÍNIMA de distância), obtida com o próprio algoritmo de Prim."""
    n = len(sim)
    dist = [[1 - sim[i][j] for j in range(n)] for i in range(n)]
    na_arvore = [False] * n
    chave = [math.inf] * n
    chave[0] = 0.0
    maior_aresta = 0.0
    for _ in range(n):
        v = min((i for i in range(n) if not na_arvore[i]), key=lambda i: chave[i])
        na_arvore[v] = True
        maior_aresta = max(maior_aresta, chave[v])
        for w in range(n):
            if not na_arvore[w] and dist[v][w] < chave[w]:
                chave[w] = dist[v][w]
    return round(1 - maior_aresta, CASAS)


def escolhe_corte(sim: list) -> float:
    if MODO_CORTE == "conexo":
        return corte_conexo(sim)
    if MODO_CORTE == "grau_minimo":
        return corte_grau_minimo(sim)
    if MODO_CORTE == "fixo":
        return CORTE_FIXO
    return -math.inf  # "nenhum"


# ----------------------------------------------------------------------
# Leitura e construção do grafo
# ----------------------------------------------------------------------

def carrega_dados() -> pd.DataFrame:
    df = pd.read_excel(CAMINHO_PLANILHA, sheet_name=ABA)
    renomeio = {
        col: _MAPA_NORMALIZADO[_normaliza_nome(col)]
        for col in df.columns
        if _normaliza_nome(col) in _MAPA_NORMALIZADO
    }
    df = df.rename(columns=renomeio)
    df = df[df["Linguagens escolhidas"].notna()].reset_index(drop=True)

    if not INCLUIR_EXCLUIDAS and "excluidas" in df.columns:
        antes = len(df)
        df = df[df["excluidas"].isna()].reset_index(drop=True)
        if antes - len(df):
            print(f"[info] {antes - len(df)} linguagem(ns) marcada(s) como excluída(s) foram ignoradas.")
    df["Linguagens escolhidas"] = df["Linguagens escolhidas"].astype(str).str.strip()
    return df


def constroi_grafo(nomes: list, sim: list, corte: float) -> GrafoNDPeso:
    """Uma aresta para cada par com similaridade >= corte.
    Peso da aresta = distância = 1 - similaridade."""
    g = GrafoNDPeso(n=len(nomes), nomes=nomes)
    for i, j in itertools.combinations(range(len(nomes)), 2):
        if sim[i][j] >= corte:
            g.insere_and(i, j, round(1 - sim[i][j], CASAS))
    return g


# ----------------------------------------------------------------------
# Exportação
# ----------------------------------------------------------------------

def nome_graphonline(nome: str) -> str:
    """O Graph Online usa '-', '>', '<' e '(peso)' na sintaxe das arestas,
    então os nomes não podem conter esses caracteres (ex.: 'Transact-SQL
    (T-SQL)'). Troca tudo que não for letra, dígito, '#', '+' ou '.' por '_'."""
    s = sem_acento(nome)
    s = re.sub(r"[^A-Za-z0-9#+.]+", "_", s)
    return s.strip("_")


def exporta_graphonline(g: GrafoNDPeso, caminho: str) -> None:
    """Formato 'Criar grafo por lista de arestas' do Graph Online:
    uma aresta por linha, a-(peso)-b = aresta não dirigida com peso."""
    rotulos = [nome_graphonline(x) for x in g.nomes]
    assert len(set(rotulos)) == len(rotulos), "nomes repetidos após a limpeza para o Graph Online"
    linhas = [f"{rotulos[i]}-({p:.{CASAS}f})-{rotulos[j]}" for i, j, p in g.lista_arestas()]
    # vértice sem aresta: escrito sozinho na linha
    for v in range(g.n):
        if g.grau_nd(v) == 0:
            linhas.append(rotulos[v])
    with open(caminho, "w", encoding="utf-8") as f:
        f.write("\n".join(linhas) + "\n")


def exporta_java(g: GrafoNDPeso, caminho: str) -> None:
    """Arquivo lido por TGrafoNDPeso.carregaArquivo():
        linha 1: tipo do grafo (2 = não orientado com peso na aresta)
        linha 2: n
        n linhas: <indice> "<nome>"
        linha: m
        m linhas: <v> <w> <peso>"""
    arestas = g.lista_arestas()
    with open(caminho, "w", encoding="utf-8") as f:
        f.write("2\n")
        f.write(f"{g.n}\n")
        for i, nome in enumerate(g.nomes):
            f.write(f'{i} "{nome}"\n')
        f.write(f"{len(arestas)}\n")
        for i, j, p in arestas:
            f.write(f"{i} {j} {p:.{CASAS}f}\n")


def exporta_csvs(g: GrafoNDPeso, sim: list) -> None:
    linhas = [
        {"a": i, "b": j, "no_a": g.nomes[i], "no_b": g.nomes[j],
         "similaridade": sim[i][j], "distancia": p}
        for i, j, p in g.lista_arestas()
    ]
    pd.DataFrame(linhas).sort_values("distancia").to_csv(CSV_ARESTAS, index=False)
    pd.DataFrame(g.adj, index=g.nomes, columns=g.nomes).to_csv(CSV_MATRIZ)


def _lista_exibicao(valor) -> list:
    """Termos originais da célula (para mostrar na página)."""
    if _vazio(valor):
        return []
    partes = re.split(r"[,;\n]|\s/\s", str(valor))
    return [p.strip(" .") for p in partes if p.strip(" .")]


def exporta_pagina(df: pd.DataFrame, tokens: list, g: GrafoNDPeso, sim: list, corte: float, caminho: str) -> None:
    """Gera dados_grafo.js com todas as informações que a página precisa.
    A página carrega com <script src="dados_grafo.js">, o que funciona
    tanto no GitHub Pages quanto abrindo o index.html direto do disco."""
    linguagens = []
    for i, row in df.iterrows():
        linguagens.append({
            "nome": row["Linguagens escolhidas"],
            "paradigmas": sorted(tokens[i]["Paradigmas"]),
            "forteFraco": sorted(tokens[i]["Tipagem forte ou fraco"]),
            "dinEst": sorted(tokens[i]["Tipagem dinamico ou estático"]),
            "influencias": _lista_exibicao(row.get("Influência")),
            "aplicacao": "" if _vazio(row.get("Aplicação")) else str(row.get("Aplicação")).strip(),
            "sintatica": "" if _vazio(row.get("Sintática")) else str(row.get("Sintática")).strip(),
            "semantica": "" if _vazio(row.get("Semantica")) else str(row.get("Semantica")).strip(),
            "doc": "" if _vazio(row.get("Documentação")) else str(row.get("Documentação")).strip(),
        })
    dados = {
        "corte": corte,
        "modoCorte": MODO_CORTE,
        "pesos": {"jaccard": W_JACCARD, "cosseno": W_COSSENO},
        "linguagens": linguagens,
        # matriz completa de similaridade: a página pode refazer o corte
        "similaridade": sim,
    }
    with open(caminho, "w", encoding="utf-8") as f:
        f.write("// Gerado por similaridade_grafo.py — não editar à mão.\n")
        f.write("const DADOS_GRAFO = ")
        json.dump(dados, f, ensure_ascii=False, separators=(",", ":"))
        f.write(";\n")


# ----------------------------------------------------------------------
# Execução principal
# ----------------------------------------------------------------------

def main():
    df = carrega_dados()
    if len(df) < 2:
        raise ValueError(f"A aba '{ABA}' precisa ter ao menos 2 linhas (nós) após os filtros.")

    nomes = df["Linguagens escolhidas"].tolist()
    tokens = [{col: tokeniza_categorico(row.get(col), col) for col in COLUNAS_CATEGORICAS}
              for _, row in df.iterrows()]

    n = len(df)
    print(f"Calculando similaridade para {n} linguagens ({n * (n - 1) // 2} pares)...\n")
    sim = matriz_similaridade(df, tokens)

    c_grau = corte_grau_minimo(sim)
    c_conexo = corte_conexo(sim)
    print(f"Corte 'grau_minimo' (todo vértice com >= 1 aresta): {c_grau:.4f}")
    print(f"Corte 'conexo'      (grafo conexo):                 {c_conexo:.4f}")

    corte = escolhe_corte(sim)
    g = constroi_grafo(nomes, sim, corte)
    print(f"\nModo de corte: {MODO_CORTE} -> similaridade >= {corte:.4f} "
          f"(distância <= {1 - corte:.4f})")
    print(f"Grafo montado com {g.n} vértices e {g.m} arestas. Conexo: {g.conexo()}\n")

    arestas = sorted(g.lista_arestas(), key=lambda a: a[2])
    print("=== 10 pares MAIS próximos (menor distância) ===")
    for i, j, p in arestas[:10]:
        print(f"  {g.nomes[i]:<28} {g.nomes[j]:<28} dist={p:.4f}  sim={sim[i][j]:.4f}")

    graus = sorted(range(g.n), key=lambda v: -g.grau_nd(v))
    print("\n=== 5 vértices de maior grau ===")
    for v in graus[:5]:
        print(f"  {g.nomes[v]:<28} grau {g.grau_nd(v)}")

    exporta_csvs(g, sim)
    exporta_graphonline(g, TXT_GRAPHONLINE)
    exporta_java(g, TXT_JAVA)
    exporta_pagina(df, tokens, g, sim, corte, JS_PAGINA)
    print(f"\nArquivos gerados: {CSV_ARESTAS}, {CSV_MATRIZ}, {TXT_GRAPHONLINE}, {TXT_JAVA}, {JS_PAGINA}")


if __name__ == "__main__":
    main()