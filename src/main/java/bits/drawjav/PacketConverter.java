/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;


public interface PacketConverter<T> extends Closeable {
    public void requestFormat( StreamFormat format );
    public StreamFormat requestedFormat();
    public StreamFormat sourceFormat();
    public StreamFormat destFormat();
    public T convert( T packet ) throws IOException;
    public T drain() throws IOException;
    public void clear();
    public void close() throws IOException;
}
