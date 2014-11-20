/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import bits.jav.JavException;
import bits.jav.codec.JavFrame;
import bits.util.ref.AbstractRefable;
import bits.util.ref.Refable;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;


/**
 * OneStreamVideoAllocator is a VideoPacket allocator that is optimized for a single
 * format of pictures. Attempts to allocate different formats with the same allocator instance
 * will produce poor performance and generate a warning.
 * <p>
 * OneStreamVideoAllocator uses a hard-referenced memory pool.
 *
 * @author Philip DeCamp
 */
public class OneStreamVideoAllocator extends AbstractRefable implements VideoAllocator {

    private static final Logger sLog = Logger.getLogger( OneStreamVideoAllocator.class.getName() );

    private final Stack<VideoPacket> mPool = new Stack<VideoPacket>();
    private final long mByteCap;
    private       int  mItemCap;

    private PictureFormat mPoolFormat;
    private long          mPoolSize;

    private boolean mHasFormat        = false;
    private boolean mHasChangedFormat = false;
    private boolean mDropping         = false;

    // Number of frames not returned.
    private int mOutstandingNum         = 0;
    private int mOutstandingCountThresh = 1000;


    public OneStreamVideoAllocator( int itemMax, long byteMax ) {
        mItemCap = itemMax;
        mByteCap = byteMax;
    }


    @Override
    public synchronized VideoPacket alloc( PictureFormat format ) {
        if( !checkFormat( format, mPoolFormat ) ) {
            format = setPoolFormat( format );
        }

        mHasFormat = true;
        VideoPacket packet = poll();
        if( packet != null ) {
            return packet;
        }

        mOutstandingNum++;

        if( mOutstandingNum > mOutstandingCountThresh && mOutstandingCountThresh > 0 ) {
            mOutstandingCountThresh = -1;
            sLog.warning( "Video frames not being recycled. There is likely a memory leak." );
        }

        if( format != null ) {
            try {
                return VideoPacket.createFilled( this, format );
            } catch( JavException ex ) {
                throw new RuntimeException( ex );
            }
        } else {
            return VideoPacket.createAuto( this );
        }
    }


    public synchronized boolean offer( VideoPacket obj ) {
        if( mDropping ) {
            return false;
        }

        mOutstandingNum--;

        if( mItemCap >= 0 && mPool.size() >= mItemCap ) {
            return false;
        }

        PictureFormat fmt = obj.pictureFormat();
        if( !checkFormat( fmt, mPoolFormat ) ) {
            return false;
        }

        long size = itemSize( obj );
        if( mByteCap >= 0 && mPoolSize + size >= mByteCap ) {
            return false;
        }

        mPool.push( obj );
        mPoolSize += size;
        return true;
    }


    public synchronized VideoPacket poll() {
        int n = mPool.size();
        switch( n ) {
        case 0:
            return null;
        case 1:
            mPoolSize = 0;
            mOutstandingNum++;
            return mPool.pop();
        default:
            VideoPacket p = mPool.pop();
            mPoolSize -= itemSize( p );
            mOutstandingNum++;
            return p;
        }
    }

    @Override
    protected void freeObject() {
        mItemCap = 0;

        List<Refable> clear;
        synchronized( this ) {
            if( mPool.isEmpty() ) {
                return;
            }
            clear = new ArrayList<Refable>( mPool );
            mPool.clear();
        }

        for( Refable p : clear ) {
            drop( p );
        }
    }



    private boolean checkFormat( PictureFormat a, PictureFormat b ) {
        if( a == b ) {
            return true;
        }
        return a != null &&
               b != null &&
               a.width() == b.width() &&
               a.height() == b.height() &&
               a.pixelFormat() == b.pixelFormat();
    }


    private void drop( Refable ref ) {
        // Refable object likely to offer itself on deref, thus the "mDropping" var.
        // Would be better to have separate method, but I didn't want to change the Refable API.
        mDropping = true;
        ref.deref();
        mDropping = false;
    }


    private int itemSize( VideoPacket p ) {
        if( mByteCap < 0 ) {
            return 0;
        }

        int size = 0;
        ByteBuffer buf = p.javaBufElem( 0 );
        if( buf != null ) {
            size = buf.capacity();
        } else {
            PictureFormat fmt = p.pictureFormat();
            if( fmt != null ) {
                size = JavFrame.computeVideoBufferSize( fmt.width(), fmt.height(), fmt.pixelFormat() );
            }
        }
        return Math.max( 0, size ) + 256;
    }


    private PictureFormat setPoolFormat( PictureFormat format ) {
        if( !PictureFormat.isFullyDefined( format ) ) {
            return mPoolFormat;
        }

        mPoolFormat = format;

        if( mHasFormat && !mHasChangedFormat ) {
            mHasChangedFormat = true;
            sLog.warning( "OneStreamVideoAllocator is being used for mutliple video formats. Performance may be degraded." );
        }

        while( !mPool.isEmpty() ) {
            drop( mPool.pop() );
        }

        return format;
    }

}

