package org.json.exceptions;


public class JSONIOException extends RuntimeException
{
    
    public JSONIOException()
    {
        super();
    }

    
    public JSONIOException(String message)
    {
        super(message);
    }

    
    public JSONIOException(String message, Throwable cause)
    {
        super(message, cause);
    }

    
    public JSONIOException(Throwable cause)
    {
        super(cause);
    }
}
