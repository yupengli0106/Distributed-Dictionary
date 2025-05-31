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

public class Response implements java.io.Serializable{
	private String context;
	private String word;
	private ArrayList<String> meanings;
	private ErrorCode status;

	public ErrorCode getStatus() {
		return status;
	}

	public void setStatus(ErrorCode status) {
		this.status = status;
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

	public String getContext() {
		return context;
	}

	public void setContext(String context) {
		this.context = context;
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
		channel.read(lenByte);
		lenByte.position(0);
		ByteBuffer recvBytes = ByteBuffer.allocate(lenByte.getInt());
		recvBytes.clear();
		while(recvBytes.hasRemaining()){
			channel.read(recvBytes);
		}
		ByteArrayInputStream bis = new ByteArrayInputStream(recvBytes.array());
		ObjectInputStream ois = new ObjectInputStream(bis);
		Response response = (Response)(ois.readObject());
		this.word = response.word;
		this.meanings = response.meanings;
		this.status = response.status;
		this.context = response.context;
		bis.close();
		ois.close();
		return true;
	}

}
