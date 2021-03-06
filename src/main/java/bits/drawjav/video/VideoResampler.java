/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import bits.drawjav.*;
import bits.jav.*;
import bits.jav.swscale.*;


/**
 * Manages JavFrame conversion between PictureFormats.
 * 
 * @author decamp
 */
public class VideoResampler implements PacketConverter<DrawPacket> {

    private PacketAllocator<DrawPacket> mAlloc;

    private StreamFormat mSourceFormat          = null;
    private StreamFormat mRequestedFormat       = null;
    private Stream       mDestStream            = null;
    private StreamFormat mDestFormat            = null;
    private int          mConversionFlags       = Jav.SWS_FAST_BILINEAR;

    private boolean    mNeedsInit = false;
    private SwsContext mConverter = null;

    private boolean mDisposed = false;


    public VideoResampler( PacketAllocator<DrawPacket> optAlloc ) {
        mAlloc = optAlloc;
        if( optAlloc != null ) {
            optAlloc.deref();
        }
    }



    public void allocator( PacketAllocator<DrawPacket> alloc ) {
        if( alloc != null ) {
            alloc.ref();
        }
        if( mAlloc != null ) {
            mAlloc.deref();
        }
        mAlloc = alloc;
    }


    public StreamFormat sourceFormat() {
        return mSourceFormat;
    }


    public void sourceFormat( StreamFormat format ) {
        if( format == mSourceFormat ) {
            return;
        }
        boolean match = StreamFormat.match( format, mSourceFormat );
        mSourceFormat = format;
        if( !match ) {
            updateDestFormat();
        }
    }

    /**
     * @return destination format requested by user. May be partially defined.
     */
    public StreamFormat requestedFormat() {
        return mRequestedFormat;
    }


    public void requestFormat( StreamFormat format ) {
        boolean match = StreamFormat.match( format, mRequestedFormat );
        mRequestedFormat = format;
        if( !match ) {
            updateDestFormat();
        }
    }

    /**
     * @return computed destination format. May be different from {@code #requestedFormat()}.
     */
    public StreamFormat destFormat() {
        return mDestFormat;
    }


    public int conversionFlags() {
        return mConversionFlags;
    }


    public void conversionFlags( int flags ) {
        if( flags == mConversionFlags ) {
            return;
        }
        mConversionFlags = flags;
        mNeedsInit = true;
    }




    public DrawPacket convert( DrawPacket source ) throws JavException {
        if( source.isGap() ) {
            source.ref();
            return source;
        }

        if( !StreamFormat.match( mSourceFormat, source ) ) {
            mSourceFormat = StreamFormat.fromVideoPacket( source );
            updateDestFormat();
        }

        if( mNeedsInit ) {
            init();
        }

        if( mConverter == null ) {
            source.ref();
            return source;
        }

        DrawPacket dest = mAlloc.alloc( mDestFormat, 0 );
        dest.init( mDestFormat, source.startMicros(), source.stopMicros(), false );
        dest.stream( mDestStream );
        mConverter.conv( source, dest );
        return dest;
    }


    public DrawPacket drain() {
        return null;
    }


    public void clear() {}


    public void close() {
        if( mDisposed ) {
            return;
        }

        mDisposed = true;
        mAlloc.deref();
        if( mConverter != null ) {
            mConverter.release();
            mConverter = null;
        }
    }



    private void updateDestFormat() {
        StreamFormat dest = StreamFormat.merge( mSourceFormat, mRequestedFormat );
        if( StreamFormat.match( dest, mDestFormat ) ) {
            mDestFormat = dest;
            return;
        }

        mDestFormat = dest;
        mDestStream = new BasicStream( mDestFormat );
        invalidateConverter();
    }


    private void init() throws JavException {
        mNeedsInit = false;
        StreamFormat src = mSourceFormat;
        StreamFormat dst = mDestFormat;
        
        if( src == null || dst == null || !src.isFullyDefined() || !dst.isFullyDefined() ) {
            return;
        }

        if( dst.equals( src ) ) {
            return;
        }
        
        mConverter = SwsContext.allocAndInit( src.mWidth,
                                              src.mHeight,
                                              src.mPixelFormat,
                                              dst.mWidth,
                                              dst.mHeight,
                                              dst.mPixelFormat,
                                              mConversionFlags );

        if( mAlloc == null ) {
            mAlloc = OneFormatAllocator.createPacketLimited( 8 );
        }
    }


    private void invalidateConverter() {
        if( mNeedsInit ) {
            return;
        }
        if( mConverter != null ) {
            mConverter.release();
            mConverter = null;
        }

        mNeedsInit = true;
    }


}
