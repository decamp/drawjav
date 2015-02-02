/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;

import bits.draw3d.*;
import bits.drawjav.audio.*;
import bits.drawjav.video.*;
import bits.microtime.*;


/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
public class MultiSyncedDriver extends DrawNodeAdapter implements StreamDriver {

    @Deprecated public static MultiSyncedDriver newInstance( PlayController playCont ) {
        return new MultiSyncedDriver( playCont );
    }


    private final PlayController mPlayCont;

    private final Map<Source, Node>       mSourceMap        = new HashMap<Source, Node>();
    private final Map<StreamHandle, Node> mStreamMap        = new HashMap<StreamHandle, Node>();
    private       List<Node>              mSources          = new ArrayList<Node>();
    private       long                    mSeekWarmupMicros = 2000000L;
    private       boolean                 mClosed           = false;


    public MultiSyncedDriver( PlayController playCont ) {
        mPlayCont = playCont;
    }


    public void start() {}


    public PlayController playController() {
        return mPlayCont;
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


    public synchronized StreamHandle openVideoStream( Source source,
                                                      StreamHandle stream,
                                                      PictureFormat destFormat,
                                                      Sink<? super VideoPacket> sink )
                                                      throws IOException 
    {
        return openStream( true, source, stream, destFormat, null, sink );
    }
    
    
    public synchronized StreamHandle openAudioStream( Source source,
                                                      StreamHandle stream,
                                                      AudioFormat format,
                                                      Sink<? super AudioPacket> sink )
                                                      throws IOException
    {
        return openStream( false, source, stream, null, format, sink );
    }


    @SuppressWarnings( { "unchecked", "rawtypes" } )
    private StreamHandle openStream( boolean isVideo,
                                                  Source source,
                                                  StreamHandle stream,
                                                  PictureFormat pictureFormat,
                                                  AudioFormat audioFormat,
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
            node = new Node( mPlayCont, source );
            node.mDriver.seekWarmupMicros( mSeekWarmupMicros );
            mSourceMap.put( source, node );
        }

        StreamHandle ret;
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


    public synchronized boolean closeStream( StreamHandle stream ) throws IOException {
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
    public void pushDraw( DrawEnv d ) {
        synchronized( this ) {
            for( Node s: mSources ) {
                s.mDriver.pushDraw( d );
            }
        }
    }


    private static final class Node {
        final Source mSource;
        final SyncedDriver mDriver;

        Node( PlayController playCont, Source source ) {
            mSource = source;
            mDriver = new SyncedDriver( playCont, source );
        }
    }

}
