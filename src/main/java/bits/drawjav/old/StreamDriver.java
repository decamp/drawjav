/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.old;

import java.nio.channels.Channel;

import bits.drawjav.old.StreamFormatter;
import bits.microtime.PlayClock;


/**
 * @author decamp
 */
@Deprecated
public interface StreamDriver extends Channel, StreamFormatter {
    public void start();
    public PlayClock clock();
}
