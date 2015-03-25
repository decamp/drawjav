/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

/**
 * @author decamp
 */
public class BasicStream implements Stream {

    protected final StreamFormat mFormat;

    public BasicStream( StreamFormat format ) {
        mFormat = format;
    }

    @Override
    public StreamFormat format() {
        return mFormat;
    }
}
