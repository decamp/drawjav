/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;

import bits.microtime.*;


/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
public class MultiSyncedDriver implements Ticker, StreamDriver {

    private final MemoryManager mMem;
    private final PlayClock     mClock;

    private final Map<PacketReader, Node> mSourceMap        = new HashMap<PacketReader, Node>();
    private final Map<Stream, Node>       mStreamMap        = new HashMap<Stream, Node>();
    private       List<Node>              mSources          = new ArrayList<Node>();
    private       long                    mSeekWarmupMicros = 2000000L;
    private       boolean                 mClosed           = false;


    public MultiSyncedDriver( MemoryManager mem, PlayClock clock ) {
        mMem = mem;
        mClock = clock;
    }


    public void start() {}


    public PlayClock clock() {
        return mClock;
    }


    public boolean isOpen() {
        return !mClosed;
    }


    public synchronized void close() {
        if( mClosed ) {
            return;
        }
        mClosed = true;
        mSourceMap.clear();
        mStreamMap.clear();
        while( !mSources.isEmpty() ) {
            Node node = mSources.remove( mSources.size() - 1 );
            try {
                node.mDriver.close();
            } catch( IOException ignored ) {}
        }
    }


    public synchronized Stream openVideoStream( PacketReader source,
                                                      Stream stream,
                                                      StreamFormat destFormat,
                                                      Sink<? super DrawPacket> sink )
                                                      throws IOException 
    {
        return openStream( true, source, stream, destFormat, null, sink );
    }
    
    
    public synchronized Stream openAudioStream( PacketReader source,
                                                      Stream stream,
                                                      StreamFormat format,
                                                      Sink<? super DrawPacket> sink )
                                                      throws IOException
    {
        return openStream( false, source, stream, null, format, sink );
    }


    @SuppressWarnings( { "unchecked", "rawtypes" } )
    private Stream openStream( boolean isVideo,
                                     PacketReader source,
                                     Stream stream,
                                     StreamFormat pictureFormat,
                                     StreamFormat audioFormat,
                                     Sink sink )
                                     throws IOException
    {
        if( mClosed ) {
            throw new ClosedChannelException();
        }

        Node node = mSourceMap.get( source );
        boolean newNode = false;
        if( node == null ) {
            newNode = true;
            node = new Node( mMem, mClock, source );
            node.mDriver.seekWarmupMicros( mSeekWarmupMicros );
            mSourceMap.put( source, node );
        }

        Stream ret;
        boolean abort = true;

        try {
            if( isVideo ) {
                ret = node.mDriver.openVideoStream( source, stream, pictureFormat, sink );
            } else {
                ret = node.mDriver.openAudioStream( source, stream, audioFormat, sink );
            }
            if( ret == null ) {
                return null;
            }
            abort = false;
        } finally {
            if( abort ) {
                node.mDriver.close();
            }
        }

        if( newNode ) {
            mSourceMap.put( source, node );
            mSources.add( node );
        }

        mStreamMap.put( ret, node );
        return ret;
    }


    public synchronized boolean closeStream( Stream stream ) throws IOException {
        Node node = mStreamMap.remove( stream );
        if( node == null ) {
            return false;
        }
        boolean ret = node.mDriver.closeStream( stream );
        if( !node.mDriver.hasSink() ) {
            mSourceMap.remove( node.mSource );
            mSources.remove( node );
            node.mDriver.close();
        }

        return ret;
    }

    @Override
    public synchronized void tick() {
        for( Node s: mSources ) {
            s.mDriver.tick();
        }
    }


    private static final class Node {
        final PacketReader mSource;
        final SyncedDriver mDriver;

        Node( MemoryManager mem, PlayClock clock, PacketReader source ) {
            mSource = source;
            mDriver = new SyncedDriver( mem, clock, source );
        }
    }


}
