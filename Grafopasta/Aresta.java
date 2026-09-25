/*
Carolina Lee 10440304
Pedro Casas Pequeno Junior 10437031
Pedro Gabriel Guimarães Fernandes 10437465

Esse arquivo baseicamente transforma a aresta em um classe, assim podemos
usar os dados dela como objeto, possibilitando o uso de maneira mais organizada que 
um array
*/

package Grafopasta;

// Representa uma aresta do grafo: liga dois vertices (pelo nome) e tem um peso.
public class Aresta {

	private String nomeA;
	private String nomeB;
	private float peso;

	public Aresta(String nomeA, String nomeB, float peso) {
		this.nomeA = nomeA;
		this.nomeB = nomeB;
		this.peso = peso;
	}

	public String getNomeA() {
		return nomeA;
	}

	public String getNomeB() {
		return nomeB;
	}

	public float getPeso() {
		return peso;
	}

	@Override
	public String toString() {
		return nomeA + " -- " + nomeB + " (peso " + peso + ")";
	}

}