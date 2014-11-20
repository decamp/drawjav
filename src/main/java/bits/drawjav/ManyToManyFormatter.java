/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.IOException;
import java.lang.ref.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.logging.*;

import bits.drawjav.audio.AudioFormat;
import bits.drawjav.audio.AudioPacket;
import bits.drawjav.video.PictureFormat;
import bits.drawjav.video.VideoPacket;
import bits.jav.Jav;


/**
 * Handles formatting and distribution of packets from many sources
 * to many sinks.
 * 
 * Should be pretty thread-safe/reentrant.  Of course, this may be less true
 * for the provided sinks, so I wouldn't make to many rapid open/close calls
 * while pumping packets through here.
 * 
 * @author decamp
 */
@SuppressWarnings( { "rawtypes", "unchecked" } )
public class ManyToManyFormatter implements StreamFormatter, Sink<Packet> {

    private static final Logger sLog = Logger.getLogger( ManyToManyFormatter.class.getName() );

    private final Map<StreamHandle, OneToManyFormatter> mSourceMap = new HashMap<StreamHandle, OneToManyFormatter>();
    private MemoryManager mMem;

    private final SinkCaster mCaster = new SinkCaster();

    private boolean mClosed = false;


    public ManyToManyFormatter() {
        this( null );
    }


    public ManyToManyFormatter( MemoryManager mem ) {
        if( mem == null ) {
            mMem = new PoolMemoryManager( 32, -1, 16, -1 );
        } else {
            mMem = mem;
        }
    }


    public StreamHandle openVideoStream( StreamHandle source,
                                         PictureFormat destFormat,
                                         Sink<? super VideoPacket> sink )
                                         throws IOException
    {
        final int type = source.type();
        if( type != Jav.AVMEDIA_TYPE_VIDEO ) {
            sLog.warning( "Attempt to open non-video stream as video stream." );
            return null;
        }

        synchronized( this ) {
            if( mClosed ) {
                throw new ClosedChannelException();
            }
            
            OneToManyFormatter pipe = mSourceMap.get( source );
            boolean addPipe = false;
            if( pipe == null ) {
                pipe = new OneToManyFormatter( source, mMem );
                addPipe = true;
            }
            
            StreamHandle dest = pipe.openVideoStream( destFormat, sink );
            if( addPipe  ) {
                addDest( source, pipe );
            }

            return new DestStream( source, dest );
        }
    }
    
    
    public StreamHandle openAudioStream( StreamHandle source,
                                         AudioFormat destFormat,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException
    {
        final int type = source.type();
        if( type != Jav.AVMEDIA_TYPE_AUDIO ) {
            sLog.warning( "Attempt to open non-audio stream as audio stream." );
            return null;
        }
        
        synchronized( this ) {
            if( mClosed ) {
                throw new ClosedChannelException();
            }
            
            OneToManyFormatter pipe = mSourceMap.get( source );
            boolean addPipe = false;
            if( pipe == null ) {
                pipe = new OneToManyFormatter( source, mMem );
                addPipe = true;
            }

            StreamHandle dest = pipe.openAudioStream( destFormat, sink );

            if( addPipe  ) {
                addDest( source, pipe );
            }
            
            return new DestStream( source, dest );
        }
    }
    
    
    public boolean closeStream( StreamHandle stream ) {
        if( !( stream instanceof DestStream ) ) {
            return false;
        }
        
        DestStream s = (DestStream)stream;
        StreamHandle srcHandle = s.mSource.get();
        StreamHandle dstHandle = s.mDest.get();
        
        if( srcHandle == null || dstHandle == null ) {
            return false;
        }
        
        OneToManyFormatter dest;
        boolean closeDest = false;
        boolean ret       = false;
        
        synchronized( this ) {
            dest = mSourceMap.get( srcHandle );
            if( dest == null ) {
                return false;
            }
            
            closeDest = !dest.hasSinkOtherThan( dstHandle );
            if( closeDest ) {
                removeDest( srcHandle );
            }
        }
        
        try {
            if( closeDest ) {
                dest.close();
            } else {
                dest.closeStream( dstHandle );
            }
        } catch( IOException ex ) {
            sLog.log( Level.WARNING, "Failed to close resampler.", ex );
        }
        
        return ret;
    }
    
    
    public void consume( Packet packet ) throws IOException {
        Sink sink = null;
        synchronized( this ) {
            sink = mSourceMap.get( packet.stream() );
        }
        if( sink != null ) {
            sink.consume( packet );
        }
    }
    
    
    public void clear() {
        mCaster.clear();
    }
    
    
    public void close() {
        synchronized( this ) {
            if( mClosed ) {
                return;
            }
            mClosed = true;
            mSourceMap.clear();
        }
        
        try {
            mCaster.close();
        }catch( IOException ex ) {
            sLog.log( Level.WARNING, "Failed to close stream.", ex );
        }
    }

    
    public boolean isOpen() {
        return !mClosed;
    }
    
    
    public synchronized Sink directSink( StreamHandle stream ) {
        //TODO: This should return a wrapper around the stream that intercepts "close()" calls.
        if( !( stream instanceof DestStream ) ) {
            return null;
        }
        
        DestStream ds = (DestStream)stream;
        return mSourceMap.get( ds.mDest );
    }
    
    
    public synchronized boolean hasSink() {
        return !mSourceMap.isEmpty();
    }
    
    
    public synchronized boolean isSourceActive( StreamHandle source ) {
        return mSourceMap.containsKey( source );
    }
    
    
    public synchronized boolean hasSinkOtherThan( StreamHandle stream ) {
        int size = mSourceMap.size();
        if( size < 1 ) {
            return false;
        } else if( size > 1 ) {
            return true;
        }
        if( !( stream instanceof DestStream ) ) {
            return false;
        }
            
        DestStream s = (DestStream)stream;
        StreamHandle srcHandle = s.mSource.get();
        StreamHandle dstHandle = s.mDest.get();
        if( srcHandle == null || dstHandle == null ) {
            return true;
        }
        
        OneToManyFormatter dest = mSourceMap.get( srcHandle );
        if( dest == null ) {
            return true;
        }
        
        return dest.hasSinkOtherThan( dstHandle );
    }
    
    
    public StreamHandle destToSource( StreamHandle stream ) {
        if( !( stream instanceof DestStream ) ) {
            return null;
        }
        
        return ((DestStream)stream).mSource.get();
    }
    

    public MemoryManager memoryManager() {
        return mMem;
    }


    public synchronized void memoryManager( MemoryManager mem ) {
        assert( mem != null );
        mMem = mem;
        for( OneToManyFormatter s: mSourceMap.values() ) {
            s.memoryManager( mem );
        }
    }


    
    private synchronized void addDest( StreamHandle key, OneToManyFormatter pipe ) {
        mSourceMap.put( key, pipe );
        mCaster.addSink( pipe );
    }

    
    private synchronized void removeDest( StreamHandle key ) {
        OneToManyFormatter pipe = mSourceMap.remove( key );
        if( pipe != null ) {
            mCaster.removeSink( pipe );
        }
    }
    

    private static final class DestStream extends BasicStreamHandle {
        
        final Reference<StreamHandle> mSource;
        final Reference<StreamHandle> mDest;
        
        DestStream( StreamHandle source,
                    StreamHandle dest )
        {
            super( dest.type(), 
                   dest.pictureFormat(), 
                   dest.audioFormat() );
            mSource = new WeakReference<StreamHandle>( source );
            mDest   = new WeakReference<StreamHandle>( dest );
        }
    }
    
}
