/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

        import java.nio.channels.Channel;

        import bits.microtime.PlayController;


/**
 * @author decamp
 */
public interface MultiSourceDriver extends Channel, StreamFormatter {
    public void start();
    public boolean addSource( Source source );
    public boolean removeSource( Source source );
    public PlayController playController();
}
