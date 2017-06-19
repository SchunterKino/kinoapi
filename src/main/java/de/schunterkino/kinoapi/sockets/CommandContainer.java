package de.schunterkino.kinoapi.sockets;

public class CommandContainer<T> {
	public T cmd;
	public int value;

	public CommandContainer(T cmd) {
		this.cmd = cmd;
		this.value = 0;
	}

	public CommandContainer(T cmd, int value) {
		this.cmd = cmd;
		this.value = value;
	}
}
