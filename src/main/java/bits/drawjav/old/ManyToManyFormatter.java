/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.old;

import java.io.IOException;
import java.lang.ref.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.logging.*;

import bits.drawjav.*;
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
@Deprecated
public class ManyToManyFormatter implements StreamFormatter, Sink<Packet> {

    private static final Logger sLog = Logger.getLogger( ManyToManyFormatter.class.getName() );

    private final Map<Stream, OneToManyFormatter> mStreamMap = new HashMap<Stream, OneToManyFormatter>();
    private MemoryManager mMem;

    private final SinkCaster mCaster = new SinkCaster();

    private boolean mClosed = false;


    public ManyToManyFormatter() {
        this( null );
    }


    public ManyToManyFormatter( MemoryManager optMem ) {
        if( optMem == null ) {
            mMem = new PoolPerFormatMemoryManager( 32, 16 );
        } else {
            mMem = optMem;
        }
    }


    public Stream openVideoStream( PacketReader ignored,
                                   Stream stream,
                                   StreamFormat destFormat,
                                   Sink<? super DrawPacket> sink )
            throws IOException
    {
        final int type = stream.format().mType;
        if( type != Jav.AVMEDIA_TYPE_VIDEO ) {
            sLog.warning( "Attempt to open non-video stream as video stream." );
            return null;
        }

        synchronized( this ) {
            if( mClosed ) {
                throw new ClosedChannelException();
            }

            OneToManyFormatter pipe = mStreamMap.get( stream );
            boolean addPipe = false;
            if( pipe == null ) {
                pipe = new OneToManyFormatter( stream, mMem );
                addPipe = true;
            }
            
            Stream dest = pipe.openVideoStream( destFormat, sink );
            if( addPipe  ) {
                addDest( stream, pipe );
            }

            return new DestStream( stream, dest );
        }
    }
    
    
    public Stream openAudioStream( PacketReader ignored,
                                   Stream source,
                                   StreamFormat destFormat,
                                   Sink<? super DrawPacket> sink )
                                   throws IOException
    {
        final int type = source.format().mType;
        if( type != Jav.AVMEDIA_TYPE_AUDIO ) {
            sLog.warning( "Attempt to open non-audio stream as audio stream." );
            return null;
        }
        
        synchronized( this ) {
            if( mClosed ) {
                throw new ClosedChannelException();
            }
            
            OneToManyFormatter pipe = mStreamMap.get( source );
            boolean addPipe = false;
            if( pipe == null ) {
                pipe = new OneToManyFormatter( source, mMem );
                addPipe = true;
            }

            Stream dest = pipe.openAudioStream( destFormat, sink );

            if( addPipe  ) {
                addDest( source, pipe );
            }
            
            return new DestStream( source, dest );
        }
    }
    
    
    public boolean closeStream( Stream stream ) {
        if( !( stream instanceof DestStream ) ) {
            return false;
        }
        
        DestStream s = (DestStream)stream;
        Stream srcHandle = s.mSource.get();
        Stream dstHandle = s.mDest.get();
        
        if( srcHandle == null || dstHandle == null ) {
            return false;
        }
        
        OneToManyFormatter dest;
        boolean closeDest = false;
        boolean ret       = false;
        
        synchronized( this ) {
            dest = mStreamMap.get( srcHandle );
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
            sink = mStreamMap.get( packet.stream() );
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
            mStreamMap.clear();
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
    
    
    public synchronized Sink directSink( Stream stream ) {
        //TODO: This should return a wrapper around the stream that intercepts "close()" calls.
        if( !( stream instanceof DestStream ) ) {
            return null;
        }
        
        DestStream ds = (DestStream)stream;
        return mStreamMap.get( ds.mDest.get() );
    }
    
    
    public synchronized boolean hasSink() {
        return !mStreamMap.isEmpty();
    }


    public synchronized boolean hasSinkFor( Stream source ) {
        return mStreamMap.containsKey( source );
    }
    
    
    public synchronized boolean hasSinkOtherThan( Stream stream ) {
        int size = mStreamMap.size();
        if( size < 1 ) {
            return false;
        } else if( size > 1 ) {
            return true;
        }
        if( !( stream instanceof DestStream ) ) {
            return false;
        }
            
        DestStream s = (DestStream)stream;
        Stream srcHandle = s.mSource.get();
        Stream dstHandle = s.mDest.get();
        if( srcHandle == null || dstHandle == null ) {
            return true;
        }
        
        OneToManyFormatter dest = mStreamMap.get( srcHandle );
        if( dest == null ) {
            return true;
        }
        
        return dest.hasSinkOtherThan( dstHandle );
    }
    
    
    public Stream destToSource( Stream stream ) {
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
        for( OneToManyFormatter s: mStreamMap.values() ) {
            s.memoryManager( mem );
        }
    }


    
    private synchronized void addDest( Stream key, OneToManyFormatter pipe ) {
        mStreamMap.put( key, pipe );
        mCaster.addSink( pipe );
    }

    
    private synchronized void removeDest( Stream key ) {
        OneToManyFormatter pipe = mStreamMap.remove( key );
        if( pipe != null ) {
            mCaster.removeSink( pipe );
        }
    }
    

    private static final class DestStream extends BasicStream {

        final Reference<Stream> mSource;
        final Reference<Stream> mDest;

        DestStream( Stream source,
                    Stream dest )
        {
            super( dest.format() );
            mSource = new WeakReference<Stream>( source );
            mDest = new WeakReference<Stream>( dest );
        }
    }

}
