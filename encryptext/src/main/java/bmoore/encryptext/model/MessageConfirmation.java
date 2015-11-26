package bmoore.encryptext.model;

/**
 * Created by Benjamin Moore on 11/24/2015.
 */
public class MessageConfirmation {

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    public int getMessageParts() {
        return messageParts;
    }

    public void setMessageParts(int messageParts) {
        this.messageParts = messageParts;
    }

    private int messageParts;
    private long messageId;

    public MessageConfirmation(int messageParts, long messageId)
    {
        this.messageId = messageId;
        this.messageParts = messageParts;
    }
}
