/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.old;

import java.io.*;
import java.nio.channels.Channel;


/**
 * @author decamp
 */
@Deprecated
public interface Sink<T> extends Channel {
    public void consume( T packet ) throws IOException;
    public void clear();
}
