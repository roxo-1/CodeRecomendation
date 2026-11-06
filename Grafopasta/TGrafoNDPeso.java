/*
Code Recomendation - Teoria dos Grafos (Mackenzie)

Integrantes:
  Carolina Lee                       - RA 10440304 - 10440304@mackenzista.com.br
  Pedro Casas Pequeno Junior         - RA 10437031 - 10437031@mackenzista.com.br
  Pedro Gabriel Guimarães Fernandes  - RA 10437465 - 10437465@mackenzista.com.br

Síntese:
  Grafo NÃO DIRIGIDO e PONDERADO representado por matriz de adjacência.
  Cada vértice é uma linguagem de programação e o peso da aresta é a
  DISTÂNCIA entre duas linguagens (distância = 1 - similaridade), calculada
  pelo script similaridade_grafo.py. Ausência de aresta = infinito.

  Este arquivo reúne, numa só classe, tudo o que o projeto usa:
    - estrutura e operações básicas: insereAND, removeAND, removeVND,
      showND, grauND
    - leitura/gravação: carregaArquivo (grafo_java.txt), carregaCSV
      (lista_arestas.csv), gravaArquivo, gravaMatriz
    - Busca em profundidade (DFS), conexidade e componentes
    - Dijkstra (uma ou várias origens) e reconstrução do caminho
    - Floyd (matriz de distâncias D e de rotas R) e caminho entre i e j
    - Prim (árvore geradora mínima)
    - Recomendação: linguagens mais próximas das que o usuário conhece,
      trilha de aprendizado entre duas linguagens, sugestão para iniciantes
    - menu em terminal (main)

  Vértices numerados de 0 a n-1. Como 0 é um vértice válido, "sem rota" /
  "sem antecessor" é representado por -1.

Como executar (a partir da pasta que contém a pasta Grafopasta):
  javac Grafopasta/TGrafoNDPeso.java
  java Grafopasta.TGrafoNDPeso grafo_java.txt
*/

package Grafopasta;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

// Estrutura Matriz de Adjacência para um grafo NÃO DIRIGIDO e PONDERADO
public class TGrafoNDPeso {
	private static final float INF = Float.POSITIVE_INFINITY;

	// Atributos Privados
	private int n;            // quantidade de vértices
	private int m;            // quantidade de arestas
	private float adj[][];    // matriz de adjacência com pesos (distâncias)
	private String nomes[];   // nome de cada vértice (linguagem)

	// Floyd
	private float D[][];      // matriz de distâncias mínimas
	private int R[][];        // matriz de rotas (próximo vértice no caminho)
	private boolean floydAtualizado = false;

	// Dijkstra (resultado da última execução)
	private float dist[];     // distância mínima da(s) origem(ns) até cada vértice
	private int rot[];        // antecessor de cada vértice no caminho mínimo
	private int origemDe[];   // qual origem alcançou cada vértice (várias origens)

	// Prim (resultado da última execução)
	private float custoPrim;
	private int arestasPrim[][];
	private int qtdArestasPrim;

	// =====================================================================
	// Construtores
	// =====================================================================

	public TGrafoNDPeso(int n) {
		this(n, null);
	}

	public TGrafoNDPeso(int n, String[] nomes) {
		this.n = n;
		// No início dos tempos não há arestas
		this.m = 0;
		this.adj = new float[n][n];
		// Inicia a matriz com "infinito" (sem aresta)
		for (int i = 0; i < n; i++)
			for (int j = 0; j < n; j++)
				this.adj[i][j] = INF;

		this.nomes = new String[n];
		for (int i = 0; i < n; i++)
			this.nomes[i] = (nomes != null && i < nomes.length) ? nomes[i] : String.valueOf(i);
	}

	// =====================================================================
	// Acesso
	// =====================================================================

	public int getN() { return n; }
	public int getM() { return m; }
	public String getNome(int v) { return nomes[v]; }
	public float getPeso(int v, int w) { return adj[v][w]; }

	private boolean valido(int v) {
		return v >= 0 && v < n;
	}

	// Procura o índice de uma linguagem pelo nome (sem diferenciar
	// maiúsculas). Aceita também o número do vértice. Retorna -1 se não achar.
	public int indiceDe(String texto) {
		if (texto == null) return -1;
		String t = texto.trim();
		if (t.isEmpty()) return -1;
		if (t.matches("\\d+")) {
			int v = Integer.parseInt(t);
			return valido(v) ? v : -1;
		}
		for (int i = 0; i < n; i++)
			if (nomes[i].equalsIgnoreCase(t)) return i;
		// se não achou exato, aceita um nome que COMECE com o texto
		// (ex.: "Ada" -> "Ada 2022"), desde que seja o único
		int achado = -1;
		for (int i = 0; i < n; i++) {
			if (nomes[i].toLowerCase().startsWith(t.toLowerCase())) {
				if (achado != -1) return -1; // ambíguo
				achado = i;
			}
		}
		return achado;
	}

	// =====================================================================
	// Operações básicas
	// =====================================================================

	// Insere uma aresta v-w com peso (simétrica, pois é não dirigido)
	public void insereAND(int v, int w, float peso) {
		if (!valido(v) || !valido(w) || v == w) return;
		// testa se ainda não temos a aresta
		if (adj[v][w] == INF) {
			adj[v][w] = peso;
			adj[w][v] = peso;
			m++; // atualiza qtd arestas
			floydAtualizado = false;
		}
	}

	// remove a aresta v-w do Grafo Não Dirigido
	public void removeAND(int v, int w) {
		if (!valido(v) || !valido(w)) return;
		// testa se temos a aresta
		if (adj[v][w] != INF) {
			adj[v][w] = INF;
			adj[w][v] = INF;
			m--; // atualiza qtd arestas
			floydAtualizado = false;
		}
	}

	// remove um vértice v e reconstrói a matriz (e o vetor de nomes)
	public void removeVND(int v) {
		if (!valido(v)) {
			System.out.println("Vértice inválido!");
			return;
		}
		String nomeRemovido = nomes[v];

		int novoN = n - 1;
		float[][] novaAdj = new float[novoN][novoN];
		String[] novosNomes = new String[novoN];

		int novaLinha = 0;
		for (int i = 0; i < n; i++) {
			if (i == v) continue; // pula a linha do vértice removido
			novosNomes[novaLinha] = nomes[i];
			int novaColuna = 0;
			for (int j = 0; j < n; j++) {
				if (j == v) continue; // pula a coluna do vértice removido
				novaAdj[novaLinha][novaColuna] = adj[i][j];
				novaColuna++;
			}
			novaLinha++;
		}

		// recontar arestas: como o grafo é simétrico,
		// conto só a "metade de cima" da matriz para não contar cada aresta 2x
		int novoM = 0;
		for (int i = 0; i < novoN; i++)
			for (int j = i + 1; j < novoN; j++)
				if (novaAdj[i][j] != INF)
					novoM++;

		this.adj = novaAdj;
		this.nomes = novosNomes;
		this.n = novoN;
		this.m = novoM;
		this.floydAtualizado = false;
		this.dist = null;

		System.out.println("Vértice " + v + " (" + nomeRemovido + ") removido com sucesso.");
	}

	// Apresenta o Grafo contendo número de vértices, arestas
	// e a matriz de adjacência obtida
	public void showND() {
		System.out.println("n: " + n);
		System.out.println("m: " + m);
		for (int i = 0; i < n; i++) {
			System.out.print("\n");
			for (int w = 0; w < n; w++) {
				float peso = adj[i][w];
				if (peso != INF)
					System.out.print("Adj[" + i + "," + w + "]=" + peso + " ");
				else
					System.out.print("Adj[" + i + "," + w + "]=inf ");
			}
		}
		System.out.println("\n\nfim da impressao do grafo.");
	}

	// Lista as arestas (mais legível que a matriz para 82 vértices)
	public void showArestas() {
		System.out.println("n: " + n + "  m: " + m);
		for (int i = 0; i < n; i++)
			for (int j = i + 1; j < n; j++)
				if (adj[i][j] != INF)
					System.out.printf(Locale.US, "  %s -- %s  (%.4f)%n", nomes[i], nomes[j], adj[i][j]);
	}

	// grau de um vértice v sem imprimir
	public int grau(int v) {
		int degree = 0;
		for (int i = 0; i < n; i++)
			if (adj[v][i] != INF)
				degree++;
		return degree;
	}

	// grau de um vértice v (não dirigido: basta contar a linha, pois é simétrico)
	public int grauND(int v) {
		int degree = grau(v);
		System.out.println("\nGrau do vértice " + v + " (" + nomes[v] + ") é " + degree);
		return degree;
	}

	// =====================================================================
	// Leitura e gravação de arquivo
	// =====================================================================

	/* Formato (gerado pelo similaridade_grafo.py em grafo_java.txt):
	     2                  <- tipo: não orientado com peso na aresta
	     n
	     0 "ABAP"           <- n linhas: índice e nome
	     ...
	     m
	     0 1 0.6518         <- m linhas: v w peso
	*/
	public static TGrafoNDPeso carregaArquivo(String caminho) throws IOException {
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(caminho), StandardCharsets.UTF_8))) {
			int tipo = Integer.parseInt(proximaLinha(br));
			if (tipo != 2)
				System.out.println("[aviso] tipo " + tipo + " no arquivo; lendo como não orientado com peso na aresta.");

			int qtdV = Integer.parseInt(proximaLinha(br));
			String[] nomesLidos = new String[qtdV];
			for (int i = 0; i < qtdV; i++) {
				String linha = proximaLinha(br);
				int esp = linha.indexOf(' ');
				int idx = Integer.parseInt(linha.substring(0, esp));
				String nome = linha.substring(esp + 1).trim();
				if (nome.startsWith("\"") && nome.endsWith("\"") && nome.length() >= 2)
					nome = nome.substring(1, nome.length() - 1);
				nomesLidos[idx] = nome;
			}

			TGrafoNDPeso g = new TGrafoNDPeso(qtdV, nomesLidos);
			int qtdA = Integer.parseInt(proximaLinha(br));
			for (int k = 0; k < qtdA; k++) {
				String[] p = proximaLinha(br).split("\\s+");
				g.insereAND(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Float.parseFloat(p[2]));
			}
			return g;
		}
	}

	// pula linhas em branco
	private static String proximaLinha(BufferedReader br) throws IOException {
		String linha;
		while ((linha = br.readLine()) != null) {
			linha = linha.trim();
			if (!linha.isEmpty()) return linha;
		}
		throw new IOException("Arquivo terminou antes do esperado.");
	}

	/* Lê o lista_arestas.csv gerado em Python:
	     a,b,no_a,no_b,similaridade,distancia
	   O peso da aresta é a coluna "distancia". */
	public static TGrafoNDPeso carregaCSV(String caminho) throws IOException {
		List<String[]> linhas = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(caminho), StandardCharsets.UTF_8))) {
			String cab = br.readLine(); // cabeçalho
			if (cab == null) throw new IOException("CSV vazio.");
			String linha;
			while ((linha = br.readLine()) != null)
				if (!linha.trim().isEmpty())
					linhas.add(separaCSV(linha));
		}
		int maior = -1;
		for (String[] c : linhas)
			maior = Math.max(maior, Math.max(Integer.parseInt(c[0]), Integer.parseInt(c[1])));
		String[] nomesLidos = new String[maior + 1];
		for (String[] c : linhas) {
			nomesLidos[Integer.parseInt(c[0])] = c[2];
			nomesLidos[Integer.parseInt(c[1])] = c[3];
		}
		TGrafoNDPeso g = new TGrafoNDPeso(maior + 1, nomesLidos);
		for (String[] c : linhas)
			g.insereAND(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Float.parseFloat(c[5]));
		return g;
	}

	// separa uma linha de CSV respeitando campos entre aspas
	private static String[] separaCSV(String linha) {
		List<String> campos = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		boolean aspas = false;
		for (int i = 0; i < linha.length(); i++) {
			char c = linha.charAt(i);
			if (c == '"') {
				if (aspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') { sb.append('"'); i++; }
				else aspas = !aspas;
			} else if (c == ',' && !aspas) {
				campos.add(sb.toString());
				sb.setLength(0);
			} else sb.append(c);
		}
		campos.add(sb.toString());
		return campos.toArray(new String[0]);
	}

	// grava o grafo no mesmo formato lido por carregaArquivo
	public void gravaArquivo(String caminho) throws IOException {
		try (PrintWriter pw = new PrintWriter(caminho, "UTF-8")) {
			pw.println(2);
			pw.println(n);
			for (int i = 0; i < n; i++)
				pw.println(i + " \"" + nomes[i] + "\"");
			pw.println(m);
			for (int i = 0; i < n; i++)
				for (int j = i + 1; j < n; j++)
					if (adj[i][j] != INF)
						pw.printf(Locale.US, "%d %d %.4f%n", i, j, adj[i][j]);
		}
		System.out.println("Grafo gravado em " + caminho);
	}

	// grava a matriz de adjacência num .txt (inf = sem aresta)
	public void gravaMatriz(String caminho) throws IOException {
		try (PrintWriter pw = new PrintWriter(caminho, "UTF-8")) {
			pw.println("n: " + n);
			pw.println("m: " + m);
			for (int i = 0; i < n; i++) {
				StringBuilder sb = new StringBuilder();
				for (int j = 0; j < n; j++) {
					if (j > 0) sb.append(' ');
					sb.append(adj[i][j] == INF ? "inf" : String.format(Locale.US, "%.4f", adj[i][j]));
				}
				pw.println(sb);
			}
		}
		System.out.println("Matriz gravada em " + caminho);
	}

	// =====================================================================
	// Busca em profundidade
	// =====================================================================

	/* Busca em profundidade (recursiva):
	   marca v como visitado; para cada w adjacente a v ainda não visitado,
	   visita w. Retorna a ordem de visita a partir da origem. */
	public int[] buscaProfundidade(int origem) {
		boolean[] visitado = new boolean[n];
		int[] ordem = new int[n];
		int qtd = dfs(origem, visitado, ordem, 0);
		return Arrays.copyOf(ordem, qtd);
	}

	private int dfs(int v, boolean[] visitado, int[] ordem, int qtd) {
		visitado[v] = true;
		ordem[qtd++] = v;
		for (int w = 0; w < n; w++)
			if (adj[v][w] != INF && !visitado[w])
				qtd = dfs(w, visitado, ordem, qtd);
		return qtd;
	}

	public void showBuscaProfundidade(int origem) {
		int[] ordem = buscaProfundidade(origem);
		System.out.println("\nBusca em profundidade a partir de " + nomes[origem] + ":");
		for (int i = 0; i < ordem.length; i++)
			System.out.println("  " + (i + 1) + ". " + nomes[ordem[i]]);
		System.out.println("Vértices alcançados: " + ordem.length + " de " + n);
	}

	// grafo conexo: a DFS a partir de qualquer vértice alcança todos
	public boolean conexo() {
		return n == 0 || buscaProfundidade(0).length == n;
	}

	// quantidade de componentes conexas (uma DFS por componente)
	public int componentes() {
		boolean[] visitado = new boolean[n];
		int[] ordem = new int[n];
		int qtdComp = 0;
		for (int v = 0; v < n; v++) {
			if (!visitado[v]) {
				dfs(v, visitado, ordem, 0);
				qtdComp++;
			}
		}
		return qtdComp;
	}

	// =====================================================================
	// Dijkstra
	// =====================================================================

	/* Algoritmo de Dijkstra (pesos não negativos):
	     d[origem] = 0; d[v] = inf para os demais; A = V
	     enquanto A não vazio:
	        r = vértice de A com menor d[r]; A = A - {r}
	        para todo i adjacente a r e em A:
	           se d[r] + v(r,i) < d[i] então d[i] = d[r] + v(r,i); rot[i] = r
	   A versão com várias origens começa com d = 0 em TODAS elas: o
	   resultado é a distância de cada vértice até a origem MAIS PRÓXIMA. */
	public void dijkstra(int origem) {
		dijkstra(new int[] { origem });
	}

	public void dijkstra(int[] origens) {
		dist = new float[n];
		rot = new int[n];
		origemDe = new int[n];
		boolean[] fechado = new boolean[n];
		Arrays.fill(dist, INF);
		Arrays.fill(rot, -1);
		Arrays.fill(origemDe, -1);
		for (int o : origens) {
			if (!valido(o)) continue;
			dist[o] = 0;
			origemDe[o] = o;
		}

		for (int passo = 0; passo < n; passo++) {
			// r = vértice aberto com menor distância
			int r = -1;
			for (int i = 0; i < n; i++)
				if (!fechado[i] && (r == -1 || dist[i] < dist[r]))
					r = i;
			if (r == -1 || dist[r] == INF) break; // resto é inalcançável
			fechado[r] = true;

			for (int i = 0; i < n; i++) {
				if (!fechado[i] && adj[r][i] != INF && dist[r] + adj[r][i] < dist[i]) {
					dist[i] = dist[r] + adj[r][i];
					rot[i] = r;
					origemDe[i] = origemDe[r];
				}
			}
		}
	}

	public float distanciaDijkstra(int v) {
		return dist == null ? INF : dist[v];
	}

	// caminho da origem (da última execução de Dijkstra) até destino
	public List<Integer> caminhoDijkstra(int destino) {
		List<Integer> caminho = new ArrayList<>();
		if (dist == null || dist[destino] == INF) return caminho;
		for (int v = destino; v != -1; v = rot[v])
			caminho.add(0, v);
		return caminho;
	}

	// =====================================================================
	// Floyd
	// =====================================================================

	/* para k = 0..n-1, i = 0..n-1, j = 0..n-1:
	     se (i != j) e (d[i][k] + d[k][j] < d[i][j]) então
	        d[i][j] = d[i][k] + d[k][j];  r[i][j] = r[i][k] */
	public void floyd() {
		D = new float[n][n];
		R = new int[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i == j) {
					D[i][j] = 0;
					R[i][j] = -1;      // sem rota para si mesmo
				} else if (adj[i][j] != INF) {
					D[i][j] = adj[i][j];
					R[i][j] = j;       // o próximo vértice para ir de i até j é j
				} else {
					D[i][j] = INF;
					R[i][j] = -1;      // sem rota direta
				}
			}
		}
		for (int k = 0; k < n; k++)
			for (int i = 0; i < n; i++)
				for (int j = 0; j < n; j++)
					if (i != j && D[i][k] != INF && D[k][j] != INF && D[i][k] + D[k][j] < D[i][j]) {
						D[i][j] = D[i][k] + D[k][j];
						R[i][j] = R[i][k];
					}
		floydAtualizado = true;
	}

	public void showD() {
		if (!floydAtualizado) floyd();
		System.out.println("\nMatriz de distancias D:");
		for (int i = 0; i < n; i++) {
			System.out.println();
			for (int j = 0; j < n; j++) {
				if (D[i][j] == INF) System.out.print("D[" + i + "," + j + "]= inf ");
				else System.out.printf(Locale.US, "D[%d,%d]= %.4f ", i, j, D[i][j]);
			}
		}
		System.out.println("\n\nfim da impressao da matriz D.");
	}

	public void showR() {
		if (!floydAtualizado) floyd();
		System.out.println("\nMatriz de rotas R: (-1 = sem rota ou diagonal)");
		for (int i = 0; i < n; i++) {
			System.out.println();
			for (int j = 0; j < n; j++)
				System.out.print("R[" + i + "," + j + "]= " + R[i][j] + " ");
		}
		System.out.println("\n\nfim da impressao da matriz R.");
	}

	/* Caminho pela matriz de rotas: seja r[i][j] = k1; se k1 = j o caminho é
	   (i, j); senão seja r[k1][j] = k2, ... e assim sucessivamente até chegar
	   em j (sem limite de saltos). */
	public List<Integer> caminhoFloyd(int i, int j) {
		if (!floydAtualizado) floyd();
		List<Integer> caminho = new ArrayList<>();
		if (!valido(i) || !valido(j)) return caminho;
		if (i == j) { caminho.add(i); return caminho; }
		if (R[i][j] == -1) return caminho; // não existe caminho
		caminho.add(i);
		int k = i;
		while (k != j) {
			k = R[k][j];
			caminho.add(k);
		}
		return caminho;
	}

	public float distanciaFloyd(int i, int j) {
		if (!floydAtualizado) floyd();
		return D[i][j];
	}

	// =====================================================================
	// Prim
	// =====================================================================

	/* Algoritmo de PRIM (slide):
	   T = { raiz }; E' = vazio; custo = 0
	   enquanto T != V:
	     valor = inf
	     para todo k em T, para todo i em V - T:
	        se v(k,i) < valor então valor = v(k,i); vint = k; vext = i
	     custo = custo + valor; T = T U {vext}; E' = E' U {(vint, vext)}
	   (a recursão do slide foi trocada por um laço, que faz o mesmo) */
	public float prim(int raiz) {
		custoPrim = 0;
		arestasPrim = new int[Math.max(n - 1, 0)][2];
		qtdArestasPrim = 0;
		if (!valido(raiz)) return INF;

		boolean[] emT = new boolean[n];
		emT[raiz] = true;
		int qtdT = 1;

		while (qtdT < n) {
			float valor = INF;
			int vint = -1, vext = -1;
			for (int k = 0; k < n; k++) {
				if (!emT[k]) continue;                 // k em T
				for (int i = 0; i < n; i++) {
					if (emT[i]) continue;              // i em V - T
					if (adj[k][i] < valor) {
						valor = adj[k][i];
						vint = k;
						vext = i;
					}
				}
			}
			if (vext == -1) { // não dá pra chegar nos vértices restantes
				System.out.println("Grafo desconexo: a árvore cobre só " + qtdT + " de " + n + " vértices.");
				break;
			}
			custoPrim += valor;
			emT[vext] = true;
			qtdT++;
			arestasPrim[qtdArestasPrim][0] = vint;
			arestasPrim[qtdArestasPrim][1] = vext;
			qtdArestasPrim++;
		}
		return custoPrim;
	}

	public void showPrim(int raiz) {
		prim(raiz);
		System.out.println("\nÁrvore geradora mínima (Prim) a partir de " + nomes[raiz] + ":");
		for (int j = 0; j < qtdArestasPrim; j++) {
			int a = arestasPrim[j][0], b = arestasPrim[j][1];
			System.out.printf(Locale.US, "  (%s, %s)  %.4f%n", nomes[a], nomes[b], adj[a][b]);
		}
		System.out.printf(Locale.US, "Arestas: %d   Custo total: %.4f%n", qtdArestasPrim, custoPrim);
	}

	// =====================================================================
	// Recomendação
	// =====================================================================

	// vizinhos diretos de v, do mais próximo (menor peso) ao mais distante
	public int[] vizinhosMaisProximos(int v, int k) {
		List<Integer> viz = new ArrayList<>();
		for (int w = 0; w < n; w++)
			if (adj[v][w] != INF) viz.add(w);
		viz.sort((a, b) -> Float.compare(adj[v][a], adj[v][b]));
		int tam = Math.min(k, viz.size());
		int[] r = new int[tam];
		for (int i = 0; i < tam; i++) r[i] = viz.get(i);
		return r;
	}

	/* Recomenda as k linguagens mais próximas do conjunto que o usuário
	   já conhece: Dijkstra com várias origens (as linguagens conhecidas)
	   e ordenação pela distância mínima. */
	public int[] recomenda(int[] conhecidas, int k) {
		dijkstra(conhecidas);
		boolean[] conhece = new boolean[n];
		for (int c : conhecidas) if (valido(c)) conhece[c] = true;
		List<Integer> cand = new ArrayList<>();
		for (int v = 0; v < n; v++)
			if (!conhece[v] && dist[v] != INF) cand.add(v);
		cand.sort((a, b) -> Float.compare(dist[a], dist[b]));
		int tam = Math.min(k, cand.size());
		int[] r = new int[tam];
		for (int i = 0; i < tam; i++) r[i] = cand.get(i);
		return r;
	}

	public void showRecomendacao(int[] conhecidas, int k) {
		int[] rec = recomenda(conhecidas, k);
		StringBuilder sb = new StringBuilder();
		for (int c : conhecidas) sb.append(sb.length() > 0 ? ", " : "").append(nomes[c]);
		System.out.println("\nVocê conhece: " + sb);
		System.out.println("Recomendações (da mais próxima para a mais distante):");
		for (int i = 0; i < rec.length; i++) {
			int v = rec[i];
			System.out.printf(Locale.US, "  %2d. %-28s distância %.4f   caminho: %s%n",
					i + 1, nomes[v], dist[v], textoCaminho(caminhoDijkstra(v)));
		}
	}

	// trilha de aprendizado: caminho mínimo entre duas linguagens
	public void showTrilha(int origem, int destino) {
		dijkstra(origem);
		List<Integer> c = caminhoDijkstra(destino);
		if (c.isEmpty()) {
			System.out.println("Não existe caminho de " + nomes[origem] + " até " + nomes[destino]);
			return;
		}
		System.out.println("\nTrilha de " + nomes[origem] + " até " + nomes[destino] + ":");
		for (int i = 0; i < c.size(); i++) {
			if (i == 0) System.out.println("  " + nomes[c.get(i)]);
			else System.out.printf(Locale.US, "  -> %s  (+%.4f)%n", nomes[c.get(i)], adj[c.get(i - 1)][c.get(i)]);
		}
		System.out.printf(Locale.US, "Distância total: %.4f   (%d passos)%n", dist[destino], c.size() - 1);
	}

	/* Sugestão de primeira linguagem: as de MAIOR grau são as que mais se
	   parecem com outras, então abrem caminho para mais linguagens.
	   Desempate: menor distância média para os vizinhos. */
	public int[] sugereIniciante(int k) {
		Integer[] ordem = new Integer[n];
		final float[] media = new float[n];
		for (int v = 0; v < n; v++) {
			ordem[v] = v;
			float soma = 0;
			int g = 0;
			for (int w = 0; w < n; w++)
				if (adj[v][w] != INF) { soma += adj[v][w]; g++; }
			media[v] = g == 0 ? INF : soma / g;
		}
		Arrays.sort(ordem, (a, b) -> {
			int ga = grau(a), gb = grau(b);
			if (ga != gb) return gb - ga;
			return Float.compare(media[a], media[b]);
		});
		int tam = Math.min(k, n);
		int[] r = new int[tam];
		for (int i = 0; i < tam; i++) r[i] = ordem[i];
		return r;
	}

	private String textoCaminho(List<Integer> c) {
		StringBuilder sb = new StringBuilder();
		for (int v : c) sb.append(sb.length() > 0 ? " -> " : "").append(nomes[v]);
		return sb.toString();
	}

	public void showLinguagens() {
		for (int i = 0; i < n; i++)
			System.out.printf("  %2d  %-28s grau %d%n", i, nomes[i], grau(i));
	}

	// =====================================================================
	// Menu em terminal
	// =====================================================================

	public static void main(String[] args) {
		String arquivo = args.length > 0 ? args[0] : "grafo_java.txt";
		TGrafoNDPeso g;
		try {
			g = arquivo.toLowerCase().endsWith(".csv") ? carregaCSV(arquivo) : carregaArquivo(arquivo);
		} catch (IOException | RuntimeException e) {
			System.out.println("Não foi possível ler " + arquivo + ": " + e.getMessage());
			return;
		}
		System.out.println("Grafo carregado de " + arquivo + ": " + g.n + " linguagens, " + g.m + " arestas.");

		Scanner sc = new Scanner(System.in, "UTF-8");
		while (true) {
			System.out.println("\n========== Code Recomendation ==========");
			System.out.println(" 1  Resumo do grafo (n, m, conexidade)");
			System.out.println(" 2  Listar linguagens");
			System.out.println(" 3  Mostrar matriz de adjacência");
			System.out.println(" 4  Listar arestas");
			System.out.println(" 5  Grau de uma linguagem");
			System.out.println(" 6  Vizinhos mais próximos de uma linguagem");
			System.out.println(" 7  Recomendar a partir das linguagens que conheço (Dijkstra)");
			System.out.println(" 8  Trilha de aprendizado entre duas linguagens (Dijkstra)");
			System.out.println(" 9  Sugestão de primeira linguagem (maior grau)");
			System.out.println("10  Busca em profundidade");
			System.out.println("11  Árvore geradora mínima (Prim)");
			System.out.println("12  Distância e caminho entre duas linguagens (Floyd)");
			System.out.println("13  Inserir aresta");
			System.out.println("14  Remover aresta");
			System.out.println("15  Remover linguagem (vértice)");
			System.out.println("16  Gravar grafo e matriz em arquivo");
			System.out.println(" 0  Sair");
			System.out.print("Opção: ");
			if (!sc.hasNextLine()) break;
			String op = sc.nextLine().trim();

			try {
				switch (op) {
				case "0":
					System.out.println("Até mais!");
					return;
				case "1":
					System.out.println("Vértices: " + g.n + "  Arestas: " + g.m);
					System.out.println("Conexo: " + (g.conexo() ? "sim" : "não") + "  Componentes: " + g.componentes());
					break;
				case "2":
					g.showLinguagens();
					break;
				case "3":
					g.showND();
					break;
				case "4":
					g.showArestas();
					break;
				case "5": {
					int v = leVertice(sc, g, "Linguagem: ");
					if (v != -1) g.grauND(v);
					break;
				}
				case "6": {
					int v = leVertice(sc, g, "Linguagem: ");
					if (v == -1) break;
					int k = leInteiro(sc, "Quantos vizinhos? ", 5);
					System.out.println("\nVizinhos mais próximos de " + g.nomes[v] + ":");
					for (int w : g.vizinhosMaisProximos(v, k))
						System.out.printf(Locale.US, "  %-28s distância %.4f%n", g.nomes[w], g.adj[v][w]);
					break;
				}
				case "7": {
					System.out.print("Linguagens que você conhece (separadas por vírgula): ");
					String[] partes = sc.nextLine().split(",");
					List<Integer> lista = new ArrayList<>();
					for (String p : partes) {
						if (p.trim().isEmpty()) continue;
						int v = g.indiceDe(p);
						if (v == -1) System.out.println("  [ignorada] não encontrei: " + p.trim());
						else if (!lista.contains(v)) lista.add(v);
					}
					if (lista.isEmpty()) { System.out.println("Nenhuma linguagem válida."); break; }
					int k = leInteiro(sc, "Quantas recomendações? ", 5);
					int[] conhecidas = new int[lista.size()];
					for (int i = 0; i < conhecidas.length; i++) conhecidas[i] = lista.get(i);
					g.showRecomendacao(conhecidas, k);
					break;
				}
				case "8": {
					int a = leVertice(sc, g, "Linguagem de origem (que você conhece): ");
					if (a == -1) break;
					int b = leVertice(sc, g, "Linguagem de destino (que quer aprender): ");
					if (b == -1) break;
					g.showTrilha(a, b);
					break;
				}
				case "9": {
					int k = leInteiro(sc, "Quantas sugestões? ", 5);
					System.out.println("\nSugestões para iniciantes:");
					for (int v : g.sugereIniciante(k))
						System.out.println("  " + g.nomes[v] + " (grau " + g.grau(v) + ")");
					break;
				}
				case "10": {
					int v = leVertice(sc, g, "Linguagem de origem: ");
					if (v != -1) g.showBuscaProfundidade(v);
					break;
				}
				case "11": {
					int v = leVertice(sc, g, "Linguagem raiz (Enter = 0): ", 0);
					if (v != -1) g.showPrim(v);
					break;
				}
				case "12": {
					int a = leVertice(sc, g, "Linguagem de origem: ");
					if (a == -1) break;
					int b = leVertice(sc, g, "Linguagem de destino: ");
					if (b == -1) break;
					List<Integer> c = g.caminhoFloyd(a, b);
					if (c.isEmpty()) System.out.println("Não existe caminho.");
					else System.out.printf(Locale.US, "Caminho: %s%nDistância: %.4f%n",
							g.textoCaminho(c), g.distanciaFloyd(a, b));
					break;
				}
				case "13": {
					int a = leVertice(sc, g, "Linguagem 1: ");
					if (a == -1) break;
					int b = leVertice(sc, g, "Linguagem 2: ");
					if (b == -1) break;
					System.out.print("Peso (distância entre 0 e 1): ");
					float p = Float.parseFloat(sc.nextLine().trim().replace(',', '.'));
					int antes = g.m;
					g.insereAND(a, b, p);
					System.out.println(g.m > antes ? "Aresta inserida." : "A aresta já existia (ou é um laço).");
					break;
				}
				case "14": {
					int a = leVertice(sc, g, "Linguagem 1: ");
					if (a == -1) break;
					int b = leVertice(sc, g, "Linguagem 2: ");
					if (b == -1) break;
					int antes = g.m;
					g.removeAND(a, b);
					System.out.println(g.m < antes ? "Aresta removida." : "Essa aresta não existe.");
					break;
				}
				case "15": {
					int v = leVertice(sc, g, "Linguagem a remover: ");
					if (v != -1) g.removeVND(v);
					break;
				}
				case "16":
					g.gravaArquivo("grafo_saida.txt");
					g.gravaMatriz("matriz_saida.txt");
					break;
				default:
					System.out.println("Opção inválida.");
				}
			} catch (IOException | NumberFormatException e) {
				System.out.println("Erro: " + e.getMessage());
			}
		}
	}

	private static int leVertice(Scanner sc, TGrafoNDPeso g, String msg) {
		return leVertice(sc, g, msg, -1);
	}

	private static int leVertice(Scanner sc, TGrafoNDPeso g, String msg, int padrao) {
		System.out.print(msg);
		if (!sc.hasNextLine()) return -1;
		String t = sc.nextLine();
		if (t.trim().isEmpty() && padrao != -1) return padrao;
		int v = g.indiceDe(t);
		if (v == -1) System.out.println("Linguagem não encontrada: " + t.trim() + " (use o nome ou o número da opção 2)");
		return v;
	}

	private static int leInteiro(Scanner sc, String msg, int padrao) {
		System.out.print(msg + "(Enter = " + padrao + ") ");
		if (!sc.hasNextLine()) return padrao;
		String t = sc.nextLine().trim();
		if (t.isEmpty()) return padrao;
		try { return Math.max(1, Integer.parseInt(t)); }
		catch (NumberFormatException e) { return padrao; }
	}
}
