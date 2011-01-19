/**
 * Copyright 2005-2010 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.ext.ssl.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

import org.restlet.engine.io.BufferState;
import org.restlet.engine.io.IoBuffer;
import org.restlet.engine.io.IoState;
import org.restlet.engine.io.SelectionChannel;
import org.restlet.engine.io.WritableSelectionChannel;

/**
 * SSL byte channel that wraps all application data using the SSL/TLS protocols.
 * It is important to implement {@link SelectionChannel} as some framework
 * classes rely on this down the processing chain.
 * 
 * @author Jerome Louvel
 */
public class WritableSslChannel extends SslChannel<WritableSelectionChannel>
        implements WritableSelectionChannel {

    /** The packet IO buffer. */
    private volatile IoBuffer packetBuffer;

    /**
     * Constructor.
     * 
     * @param wrappedChannel
     *            The wrapped channel.
     * @param manager
     *            The SSL manager.
     * @param connection
     *            The parent SSL connection.
     */
    public WritableSslChannel(WritableSelectionChannel wrappedChannel,
            SslManager manager, SslConnection<?> connection) {
        super(wrappedChannel, manager, connection);

        if (manager != null) {
            SSLSession session = manager.getSession();
            int packetSize = session.getPacketBufferSize();
            this.packetBuffer = new IoBuffer(getConnection().createByteBuffer(
                    packetSize));
        } else {
            this.packetBuffer = null;
        }
    }

    /**
     * Flushes the packet buffer if it is in draining state.
     * 
     * @return The number of bytes flushed.
     * @throws IOException
     */
    protected int flush() throws IOException {
        int result = 0;

        if (getPacketBuffer().isDraining()) {
            if (getWrappedChannel().isOpen()) {
                result = getWrappedChannel()
                        .write(getPacketBuffer().getBytes());

                if (getConnection().getLogger().isLoggable(Level.INFO)) {
                    getConnection().getLogger().log(Level.INFO,
                            "Packet bytes written: " + result);
                }

                if (!getPacketBuffer().getBytes().hasRemaining()) {
                    getPacketBuffer().clear();
                }
            }
        }

        return result;
    }

    /**
     * Returns the SSL/TLS packet IO buffer.
     * 
     * @return The SSL/TLS packet IO buffer.
     */
    protected IoBuffer getPacketBuffer() {
        return packetBuffer;
    }

    @Override
    protected void onDelegatedTasksCompleted() {
        if (getConnection().getOutboundWay().getIoState() == IoState.IDLE) {
            getConnection().getOutboundWay().setIoState(IoState.INTEREST);
        }
    }

    @Override
    protected SSLEngineResult runEngine(ByteBuffer applicationBuffer)
            throws IOException {
        SSLEngineResult result = null;

        if (getConnection().getLogger().isLoggable(Level.INFO)) {
            getConnection().getLogger().log(Level.INFO,
                    "Wrapping bytes with: " + getPacketBuffer());
        }

        int remaining = getPacketBuffer().getBytes().remaining();

        if (remaining > 0) {
            result = getManager().getEngine().wrap(applicationBuffer,
                    getPacketBuffer().getBytes());
            getPacketBuffer().getBytes().flip();
            remaining = getPacketBuffer().getBytes().remaining();

            if (remaining > 0) {
                getPacketBuffer().setState(BufferState.DRAINING);
            } else {
                getPacketBuffer().clear();
            }
        }

        return result;
    }

    /**
     * Writes the available bytes to the wrapped channel by wrapping them with
     * the SSL/TLS protocols.
     * 
     * @param src
     *            The source buffer.
     * @return The number of bytes written.
     */
    public int write(ByteBuffer src) throws IOException {
        int result = 0;

        if (getManager().getState() == SslState.READING_APPLICATION_DATA) {
            getManager().setState(SslState.WRITING_APPLICATION_DATA);
        }

        // If the packet buffer isn't empty, first try to flush it
        flush();

        // Refill the packet buffer
        if ((getPacketBuffer().isFilling())
                || (getManager().getState() == SslState.HANDSHAKING)) {
            int srcSize = src.remaining();

            if (srcSize > 0) {
                while (getPacketBuffer().getBytes().hasRemaining()
                        && (getConnection().getOutboundWay().getIoState() != IoState.IDLE)
                        && src.hasRemaining()) {
                    SSLEngineResult sslResult = runEngine(src);
                    handleResult(sslResult, src);
                    flush();
                }

                result = srcSize - src.remaining();
            }
        }

        return result;
    }
}