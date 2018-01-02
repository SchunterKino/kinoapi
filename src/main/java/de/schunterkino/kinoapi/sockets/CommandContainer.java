package de.schunterkino.kinoapi.sockets;

public class CommandContainer<T> {
	public T cmd;
	public int value;
	public boolean ignoreResponse;

	public CommandContainer(T cmd, int value, boolean ignoreResponse)
	{
		this.cmd = cmd;
		this.value = value;
		this.ignoreResponse = ignoreResponse;
	}
	
	public CommandContainer(T cmd, int value) {
		this(cmd, value, false);
	}
	
	public CommandContainer(T cmd, boolean ignoreResponse)
	{
		this(cmd, 0, ignoreResponse);
	}
	
	public CommandContainer(T cmd) {
		this(cmd, 0, false);
		this.ignoreResponse = false;
	}
}
