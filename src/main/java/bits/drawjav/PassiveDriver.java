/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.util.logging.*;

import bits.drawjav.audio.AudioFormat;
import bits.drawjav.video.PictureFormat;
import bits.microtime.*;


/**
 * PassiveDriver is not a real driver in that it does not handle
 * threading, but is meant as a general purpose component that
 * can simplify IO while supporting a variety of threading and
 * scheduling strategies. PassiveDriver can handle reading from
 * a single Source object, and delivery to multiple sinks.
 * 
 * @author decamp
 */
public class PassiveDriver implements StreamDriver {

    private static Logger sLog = Logger.getLogger( SyncedDriver.class.getName() );

    private final PacketReader mReader;
    private final ManyToManyFormatter mSink = new ManyToManyFormatter();

    private boolean     mClosed     = false;
    private boolean     mReadable   = true;
    private boolean     mEof        = false;
    private IOException mErrorState = null;

    private boolean mNeedSeek         = false;
    private long    mSeekMicros       = Long.MIN_VALUE;
    private long    mSeekWarmupMicros = 500000L;
    private boolean mNeedClear        = false;
    private boolean mClearOnSeek      = true;

    private Packet mNextPacket = null;


    public PassiveDriver( PacketReader reader ) {
        mReader = reader;
    }


    /**
     * @return true iff this PassiveDriver MIGHT have more packets.
     *        If this PassiveDriver has reached the end of its source
     *        or had an IO error, this method will return null.
     */
    public boolean hasNext() {
        return mReadable;
    }

    /**
     * @return true iff this PassiveDriver has reached the end of its source.
     */
    public boolean eof() {
        return mEof;
    }

    /**
     * @return true iff this PassiveDriver has somewhere to send data.
     */
    public boolean hasSink() {
        return mSink.hasSink();
    }

    /**
     * @param stream
     * @return true iff this PassiveDriver has somewhere to send specific stream of data.
     */
    public boolean hasSinkFor( StreamHandle stream ) {
        return mSink.hasSinkFor( stream );
    }
    
    /**
     * PassiveDriver will keep track of the last error that
     * occurred. This error status is erased after seek
     * operations.
     * 
     * @return current error status.
     */
    public IOException error() {
        return mErrorState;
    }
    
    
    /**
     * Seeks position in source to read packets from.
     */
    public synchronized void seek( long micros ) {
        mNeedSeek   = true;
        mSeekMicros = micros;
        mEof        = false;
        mErrorState = null;
        if( mNextPacket != null ) {
            mNextPacket.deref();
            mNextPacket = null;
        }
        updateStatus();
    }
    
    /**
     * Attempts to read next packet from source and
     * hold on to it for processing. If PassiveDriver
     * already has a packet, returns true immediately. 
     * Several attempts may be required to successfully
     * read a packet.
     * 
     * @return true iff PassiveDriver has a packet ready on return.
     *         ( Same as calling hasPacket() afterwards ).
     */
    public synchronized boolean readPacket() {
        if( !mReadable ) {
            return false;
        }
        
        if( mNeedSeek ) {
            mNeedSeek  = false;
            mNeedClear = mClearOnSeek;
            
            try {
                mReader.seek( mSeekMicros - mSeekWarmupMicros );
            } catch( IOException ex ) {
                mErrorState = ex;
                updateStatus();
                return false;
            }
        
        } else if( mNextPacket != null ) {
            return true;
        }
        
        try {
            Packet p = mReader.readNext();
            if( p == null ) {
                return false;
            }
            if( p.startMicros() < mSeekMicros ) {
                p.deref();
                return false;
            }
            mNextPacket = p;
            return true;
        } catch( InterruptedIOException ignore ) {
        } catch( EOFException ex ) {
            mEof = true;
            updateStatus();
        } catch( IOException ex ) {
            mErrorState = ex;
            updateStatus();
        }
        
        return false;
    }
    
    /**
     * @return true iff PassiveDriver is currently holding a packet.
     */
    public boolean hasCurrent() {
        return mNextPacket != null;
    }
    
    /**
     * If a packet is being held, adds reference and returns it.
     * Otherwise returns null.
     * 
     * @return current packet held by driver.
     */
    public synchronized Packet current() {
        if( mNextPacket == null ) {
            return null;
        }
        mNextPacket.ref();
        return mNextPacket;
    }
    
    /**
     * @return start time of currently held packet, or Long.MIN_VALUE if no packet.
     */
    public synchronized long currentMicros() {
        return mNextPacket == null ? Long.MIN_VALUE : mNextPacket.startMicros();
    }
    
    /**
     * Sends currently held packet to input.
     * 
     * @return true iff packet successfully sent.
     */
    public boolean sendCurrent() {
        Packet packet;
        boolean clear;
        
        synchronized( this ) {
            if( mNextPacket == null ) {
                return false;
            }
            
            packet = mNextPacket;
            mNextPacket = null;
            clear = mNeedClear;
            mNeedClear = false;
        }
        
        try {
            if( clear ) {
                mSink.clear();
            }
            //System.out.print( "** send  " ); Debug.print( (DrawPacket)packet );
            mSink.consume( packet );
            packet.deref();
            return true;
        } catch( InterruptedIOException ex ) {
            packet.deref();
            packet = null;
            return false;
        } catch( IOException ex ) {
            warn( "Failed to convert packet", ex );
            synchronized( this ) {
                if( !mNeedSeek ) {
                    mErrorState = ex;
                    updateStatus();
                }
            }
            
            return false;
        }
    }
    
    /**
     * Sends arbitrary packet to input.
     * Will clear input if clear is pending.
     * 
     * @param packet To send to input.
     * @return true if successful.
     */
    public boolean send( Packet packet ) {
        boolean clear;
        
        synchronized( this ) {
            clear = mNeedClear;
            mNeedClear = false;
        }
        
        try {
            if( clear ) {
                mSink.clear();
            }
            //System.out.print( "** send2  " ); Debug.print( (DrawPacket)packet );
            mSink.consume( packet );
            return true;
        } catch( InterruptedIOException ex ) {
            return false;
        } catch( IOException ex ) {
            warn( "Failed to convert packet", ex );
            synchronized( this ) {
                if( !mNeedSeek ) {
                    mErrorState = ex;
                    updateStatus();
                }
            }
            return false;
        }
    }
    
    /**
     * Clears currently held packet. If 
     * no packet is being held, call has
     * no effect.
     */
    public synchronized void clearCurrent() {
        if( mNextPacket != null ) {
            mNextPacket.deref();
            mNextPacket = null;
        }
    }
    
    /**
     * Clears input and currently held packet.
     */
    public void clear() {
        synchronized( this ) {
            if( mNextPacket != null ) {
                mNextPacket.deref();
                mNextPacket = null;
            }
            mNeedClear = false;
        }

        mSink.clear();
    }
    
    /**
     * After a seek() call, PassiveDriver may actually go
     * further back into the data stream and decode some
     * amount of data in order to warmup the decoder 
     * (there's currently no support for finding correct keyframes).
     * This method returns the number of micros of data processed
     * before PassiveDriver will begin returning packets.
     * 
     * @return Amount of data read after each seek operation before returning packets.
     */
    public long seekWarmupMicros() {
        return mSeekWarmupMicros;
    }
    
    /**
     * @param micros  Amount of data to read after each seek operation before returning packets.
     */
    public void seekWarmupMicros( long micros ) {
        mSeekWarmupMicros = micros;
    }
    

    public MemoryManager memoryManager() {
        return mSink.memoryManager();
    }
    

    public void memoryManager( MemoryManager mem ) {
        mSink.memoryManager( mem );
    }

    /**
     * @return true if this PassiveDriver will clear the input
     *              after a seek call.
     */
    public boolean clearOnSeek() {
        return mClearOnSeek;
    }
    
    /**
     * @param clearOnSeek Specifies whether this PassiveDriver will
     *               clear its input after every seek call.
     */
    public void clearOnSeek( boolean clearOnSeek ) {
        mClearOnSeek = clearOnSeek;
    }

    
    
    public void start() {}
    
    
    public PacketReader source() {
        return mReader;
    }
    
    
    public PlayClock clock() {
        return null;
    }

    @Override
    public synchronized StreamHandle openVideoStream( PacketReader ignored,
                                                      StreamHandle stream,
                                                      PictureFormat destFormat,
                                                      Sink<? super DrawPacket> sink )
                                                      throws IOException 
    {
        if( mClosed ) {
            throw new ClosedChannelException();
        }
        if( ignored != null && !ignored.equals( mReader ) ) {
            throw new IllegalArgumentException( "Unrecognized source." );
        }
        
        boolean active   = mSink.hasSinkFor( stream );
        StreamHandle ret = mSink.openVideoStream( ignored, stream, destFormat, sink );
        if( ret == null ) {
            return null;
        }
        
        if( !active ) {
            try {
                mReader.openStream( stream );
            } catch( IOException ex ) {
                mSink.closeStream( ret );
                throw ex;
            }
        }
        
        updateStatus();
        return ret;
    }
    

    @Override
    public synchronized StreamHandle openAudioStream( PacketReader ignored,
                                                      StreamHandle stream,
                                                      AudioFormat format,
                                                      Sink<? super DrawPacket> sink )
                                                      throws IOException 
    {
        if( mClosed ) {
            throw new ClosedChannelException();
        }
        if( ignored != null && !ignored.equals( mReader ) ) {
            throw new IllegalArgumentException( "Unrecognized source." );
        }
        
        boolean active   = mSink.hasSinkFor( stream );
        StreamHandle ret = mSink.openAudioStream( ignored, stream, format, sink );
        if( ret == null ) {
            return null;
        }
        
        if( !active ) {
            try {
                mReader.openStream( stream );
            } catch( IOException ex ) {
                mSink.closeStream( ret );
                throw ex;
            }
        }
        
        updateStatus();
        return ret;
    }

    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        if( !mSink.closeStream( stream ) ) {
            return false;
        }
        
        // Check if need to close source.
        synchronized( this ) {
            StreamHandle sourceStream = mSink.destToSource( stream );
            if( sourceStream != null && !mSink.hasSinkFor( sourceStream ) ) {
                if( !mSink.hasSinkFor( sourceStream ) ) {
                    try {
                        mReader.closeStream( sourceStream );
                    } catch( IOException ex ) {
                        warn( "Failed to close source stream.", ex );
                    }
                }
            }
        
            updateStatus();
            return true;
        }
    }
    
    
    public void close() {
        close( true );
    }
    
    
    public void close( boolean closeSource ) {
        synchronized( this ) {
            if( mClosed ) {
                return;
            }
            
            mClosed = true;
            updateStatus();
            
            if( mNextPacket != null ) {
                mNextPacket.deref();
                mNextPacket = null;
            }
        }
        
        mSink.close();
        
        if( closeSource ) {
            try {
                mReader.close();
            } catch( IOException ex ) {
                warn( "Failed to close av source.", ex );
            }
        }
    }
    
    
    public boolean isOpen() {
        return !mClosed;
    }
    
    
    
    private synchronized void updateStatus() {
        mReadable = !mClosed && !mEof && mErrorState == null && mSink.hasSink();
    }
    
    
    private static void warn( String message, Exception ex ) {
        sLog.log( Level.WARNING, message, ex );
    }

}
