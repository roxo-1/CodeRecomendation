# Similaridade de Nós — Grafo de Linguagens de Programação

Calcula a similaridade entre **todas** as linguagens de programação da
planilha `codeRecomendation_teste.xlsx` (aba `aqui 2`) e usa esse valor
como **peso da aresta** em um grafo não dirigido e ponderado, seguindo
a mesma lógica da classe Java `TGrafoNDPeso` (matriz de adjacência
simétrica, sem aresta = infinito).

## O que o script faz

1. Lê a aba `aqui 2` da planilha (todas as linguagens com os atributos
   preenchidos: Paradigmas, Tipagem, Influência, Aplicação, Sintática,
   Semantica).
2. Ignora automaticamente as 7 linguagens marcadas na coluna
   `excluidas`.
3. Para **cada par possível** de linguagens, calcula a similaridade
   combinando dois métodos:
   - **Jaccard** nos campos categóricos (Paradigmas, Tipagem forte ou
     fraco, Tipagem dinâmico ou estático, Influência).
   - **TF-IDF + cosseno** nos campos de texto livre (Aplicação,
     Sintática, Semantica).
   - `similaridade_final = 0.5 * Jaccard + 0.5 * cosseno` (pesos
     ajustáveis em `W_JACCARD` / `W_COSSENO`).
4. Monta o grafo completo (uma aresta entre cada par, com peso =
   similaridade) e exporta os resultados em dois arquivos CSV.

## Pré-requisitos

- Python 3.10+
- Bibliotecas: `pandas`, `openpyxl`, `scikit-learn`

Instale com:

```bash
pip install pandas openpyxl scikit-learn
```

(ou crie um `requirements.txt` com essas três linhas e rode
`pip install -r requirements.txt`)

## Como rodar

1. Coloque `similaridade_grafo.py` e `codeRecomendation_teste.xlsx`
   **na mesma pasta**.
2. Confira a variável `CAMINHO_PLANILHA` no topo do script — por
   padrão ela é relativa (`"codeRecomendation_teste.xlsx"`), então
   funciona se os dois arquivos estiverem juntos. Se preferir um
   caminho absoluto (ex.: no GitHub Codespaces), ajuste para algo como:
   ```python
   CAMINHO_PLANILHA = "/workspaces/CodeRecomendation/codeRecomendation_teste.xlsx"
   ```
3. Rode:
   ```bash
   python similaridade_grafo.py
   ```

## Saída esperada

No terminal:
- Quantas linguagens foram excluídas e quantas entraram no cálculo.
- Total de nós e arestas do grafo.
- Os 10 pares **mais** similares e os 10 pares **menos** similares.

Na pasta, dois arquivos novos:
- **`lista_arestas.csv`** — tabela simples `no_a, no_b, peso`, ordenada
  da maior para a menor similaridade. É a forma mais fácil de conferir
  o peso de qualquer aresta (abra no Excel/Google Sheets e use Ctrl+F).
- **`matriz_similaridade.csv`** — a matriz de adjacência completa
  (linguagem x linguagem), igual à impressa pelo `showND` do Java,
  só que em formato de planilha.

## Consultando o peso de uma aresta específica pelo código

```python
from similaridade_grafo import carrega_dados, constroi_grafo_completo

df = carrega_dados()
g = constroi_grafo_completo(df)

peso = g.peso_aresta("Python", "JavaScript")
print(peso)
```

## Ajustando o cálculo

- **Incluir as linguagens excluídas:** mude `INCLUIR_EXCLUIDAS = True`
  no topo do script.
- **Mudar o peso entre Jaccard e TF-IDF:** ajuste `W_JACCARD` e
  `W_COSSENO` (a soma não precisa ser exatamente 1.0, mas é o mais
  intuitivo).
- **Trocar a aba usada:** mude `ABA` (ex.: `"esse aqui"` para o teste
  rápido com só 2 linguagens, MATLAB e RPG).