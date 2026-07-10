package main.java.com.qodana.bank.model;

public class Message {
    private final String sender;
    private final String receiver;
    private final String content;

    public Message(String sender, String receiver, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
    }

    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getContent() { return content; }
}
