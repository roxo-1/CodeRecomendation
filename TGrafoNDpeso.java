// Estrutura Matriz de Adjacência para um grafo NÃO DIRIGIDO e PONDERADO
// (junta a ideia de peso do TGrafo com a simetria do TGrafoND)
public class TGrafoNDPeso {
	// Atributos Privados
	private int n;      // quantidade de vértices
	private int m;       // quantidade de arestas
	private float adj[][]; // matriz de adjacência com pesos

	// Métodos Públicos
	public TGrafoNDPeso(int n) { // construtor
		this.n = n;
		// No início dos tempos não há arestas
		this.m = 0;
		// alocação da matriz
		this.adj = new float[n][n];

		// Inicia a matriz com "infinito" (sem aresta)
		for (int i = 0; i < n; i++)
			for (int j = 0; j < n; j++)
				this.adj[i][j] = Float.POSITIVE_INFINITY;
	}

	// Insere uma aresta v-w com peso (simétrica, pois é não dirigido)
	public void insereAND(int v, int w, float peso) {
		// testa se ainda não temos a aresta
		if (adj[v][w] == Float.POSITIVE_INFINITY) {
			adj[v][w] = peso;
			adj[w][v] = peso;
			m++; // atualiza qtd arestas
		}
	}

	// remove a aresta v-w do Grafo Não Dirigido
	public void removeAND(int v, int w) {
		// testa se temos a aresta
		if (adj[v][w] != Float.POSITIVE_INFINITY) {
			adj[v][w] = Float.POSITIVE_INFINITY;
			adj[w][v] = Float.POSITIVE_INFINITY;
			m--; // atualiza qtd arestas
		}
	}

	// remove um vértice v e reconstrói a matriz
	public void removeVND(int v) {
		if (v < 0 || v >= n) {
			System.out.println("Vértice inválido!");
			return;
		}

		int novoN = n - 1;
		float[][] novaAdj = new float[novoN][novoN];

		int novaLinha = 0;
		for (int i = 0; i < n; i++) {
			if (i == v) continue; // pula a linha do vértice removido
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
		for (int i = 0; i < novoN; i++) {
			for (int j = i + 1; j < novoN; j++) {
				if (novaAdj[i][j] != Float.POSITIVE_INFINITY) {
					novoM++;
				}
			}
		}

		this.adj = novaAdj;
		this.n = novoN;
		this.m = novoM;

		System.out.println("Vértice " + v + " removido com sucesso.");
	}

	// Apresenta o Grafo contendo
	// número de vértices, arestas
	// e a matriz de adjacência obtida
	public void showND() {
		System.out.println("n: " + n);
		System.out.println("m: " + m);
		for (int i = 0; i < n; i++) {
			System.out.print("\n");
			for (int w = 0; w < n; w++) {
				float peso = adj[i][w];
				if (peso != Float.POSITIVE_INFINITY)
					System.out.print("Adj[" + i + "," + w + "]=" + peso + " ");
				else
					System.out.print("Adj[" + i + "," + w + "]=inf ");
			}
		}
		System.out.println("\n\nfim da impressao do grafo.");
	}

	// grau de um vértice v (não dirigido: basta contar a linha, pois é simétrico)
	public int grauND(int v) {
		int degree = 0;
		for (int i = 0; i < n; i++) {
			if (adj[v][i] != Float.POSITIVE_INFINITY) {
				degree++;
			}
		}
		System.out.println("\nGrau do vértice " + v + " é " + degree);
		return degree;
	}