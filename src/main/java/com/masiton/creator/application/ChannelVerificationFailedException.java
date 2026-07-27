package com.masiton.creator.application;

/** Raised when YouTube cannot provide a trustworthy public channel candidate. */
public class ChannelVerificationFailedException extends RuntimeException {
    public ChannelVerificationFailedException() { }
    public ChannelVerificationFailedException(Throwable cause) { super(cause); }
}
