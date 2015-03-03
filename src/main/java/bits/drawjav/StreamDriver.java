/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.nio.channels.Channel;

import bits.microtime.PlayClock;
import bits.microtime.PlayController;

/**
 * @author decamp
 */
public interface StreamDriver extends Channel, StreamFormatter {
    public void start();
    public PlayClock clock();
}
