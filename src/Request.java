/**
 * The university of Melbourne
 * COMP90015: Distributed Systems
 * Project 1
 * Author: Yupeng Li
 * Student ID: 1399160
 * Date: 06/04/2023
 */

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

public class Request implements java.io.Serializable{
	public static final int QUERY = 1;
	public static final int ADD = 2;
	public static final int REMOVE = 3;
	public static final int UPDATE = 4;
	private int op;
	private String word;
	private ArrayList<String> meanings;

	public Request() {}

	public Request(String word, int op) {
		this.word = word;
		this.op = op;
	}

	public int getOp() {
		return op;
	}

	public void setOp(int op) {
		this.op = op;
	}

	public String getWord() {
		return word;
	}

	public void setWord(String word) {
		this.word = word;
	}

	public ArrayList<String> getMeanings() {
		return meanings;
	}

	public void setMeanings(ArrayList<String> meanings) {
		this.meanings = meanings;
	}

	public boolean Send(SocketChannel channel) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(this);
		oos.flush();
		ByteBuffer buffer = ByteBuffer.allocate(4 + bos.size());
		buffer.putInt(bos.size());
		buffer.put(bos.toByteArray());
		buffer.flip();
		channel.write(buffer);
		bos.close();
		oos.close();
		return true;
	}

	public boolean Recv(SocketChannel channel) throws IOException, ClassNotFoundException {
		ByteBuffer lenByte = ByteBuffer.allocate(4);
		lenByte.clear();
		if ( channel.read(lenByte) == -1) {
			return false;
		}
		lenByte.position(0);
		ByteBuffer recvBytes = ByteBuffer.allocate(lenByte.getInt(0));
		recvBytes.clear();
		while(recvBytes.hasRemaining()){
			channel.read(recvBytes);
		}
		ByteArrayInputStream bis = new ByteArrayInputStream(recvBytes.array());
		ObjectInputStream ois = new ObjectInputStream(bis);
		Request request = (Request)(ois.readObject());
		this.word = request.word;
		this.meanings = request.meanings;
		this.op = request.op;
		bis.close();
		ois.close();
		return true;
	}
}
