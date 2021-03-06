package com.fuzz.datacontroller;

/**
 * Description: The main Error class that returns in a callback. Override this class to provide different kind
 * of errors.
 */
public class DataResponseError {

    private String message;
    private long statusCode;
    private boolean isNetworkError;
    private Throwable throwable;

    public DataResponseError(String message) {
        this.message = message;
        statusCode = 0;
        isNetworkError = false;
    }

    public DataResponseError(Throwable t) {
        throwable = t;
        message = throwable.getMessage();
        statusCode = 0;
        isNetworkError = false;
    }

    public DataResponseError() {
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setStatusCode(long statusCode) {
        this.statusCode = statusCode;
    }

    public void setNetworkError(boolean networkError) {
        isNetworkError = networkError;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public String toString() {
        return getMessage();
    }

    public String getUserFacingMessage() {
        return getMessage();
    }

    public String getMessage() {
        return message;
    }

    public long getStatusCode() {
        return statusCode;
    }

    public boolean isNetworkError() {
        return isNetworkError;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
