/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */
package org.restlet.ext.jaxrs.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

/**
 * This abstract class ease the development of {@link MessageBodyReader}s and
 * {@link MessageBodyWriter}.
 * 
 * @author Stephan Koops
 * @param <T>
 *                the type that can be read and written
 * @see MessageBodyReader
 * @see MessageBodyWriter
 */
public abstract class AbstractProvider<T> implements MessageBodyWriter<T>,
        MessageBodyReader<T> {

    /**
     * Returns the size of the given objects.
     * 
     * @param object
     *                the object to check the size
     * @return the size of the object, or -1, if it is not direct readable from
     *         the object.
     * @see MessageBodyWriter#getSize(Object)
     */
    public abstract long getSize(T object);

    protected abstract boolean isReadableAndWriteable(Class<?> type);

    public final boolean isWriteable(Class<?> type) {
        return isReadableAndWriteable(type);
    }

    public final boolean isReadable(Class<?> type) {
        return isReadableAndWriteable(type);
    }

    /**
     * Copies the data from the {@link InputStream} the {@link OutputStream}.
     * 
     * @param inputStream
     * @param entityStream
     * @throws IOException
     */
    protected void copyStream(InputStream inputStream, OutputStream entityStream)
            throws IOException {
        int noBytes;
        byte[] bytes = new byte[1024];
        while ((noBytes = inputStream.read(bytes)) >= 0)
            entityStream.write(bytes, 0, noBytes);
    }

    // TODO JSR311: Jersey schliesst den InputStream, wenn er fertig ist. Wenn
    // das Pflicht ist, dann bitte in das javadoc aufnehmen.

    protected void copyAndCloseStream(InputStream inputStream,
            OutputStream entityStream) throws IOException {
        try {
            copyStream(inputStream, entityStream);
        } finally {
            inputStream.close();
        }
    }

    /**
     * @see javax.ws.rs.ext.MessageBodyWriter#writeTo(java.lang.Object,
     *      javax.ws.rs.core.MediaType, javax.ws.rs.core.MultivaluedMap,
     *      java.io.OutputStream)
     */
    public abstract void writeTo(T t, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException;

    /**
     * @see javax.ws.rs.ext.MessageBodyReader#readFrom(java.lang.Class,
     *      javax.ws.rs.core.MediaType, javax.ws.rs.core.MultivaluedMap,
     *      java.io.InputStream)
     */
    public abstract T readFrom(Class<T> type, MediaType mediaType,
            MultivaluedMap<String, String> httpResponseHeaders, InputStream entityStream)
            throws IOException;
}