package cogmac.javdraw;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import cogmac.clocks.*;


/**
 * @author decamp
 */
public class PacketSyncer {

    private static final int COMMAND_NONE  = 0;
    private static final int COMMAND_CLOSE = 1;
    private static final int COMMAND_CLEAR = 2;
    private static final int COMMAND_SEND  = 3;
    
    private static final Logger sLog = Logger.getLogger(PacketSyncer.class.getName());
    
    
    private final PlayController mPlayCont;
    private final PlayClock mClock;
    private final PlayControl mPlayHandler;
    
    private final Thread mThread;
    private boolean mClosed = false;
    private long mCommandSequence = 0;
    
    private final Set<Pipe> mPipes        = new HashSet<Pipe>();
    private final TreeSet<Command> mQueue = new TreeSet<Command>();
    
    
    public PacketSyncer( PlayController playCont ) {
        mPlayCont = playCont;
        mClock    = playCont.clock();
        
        mPlayHandler = new PlayHandler();
        mPlayCont.caster().addListener( mPlayHandler );
        
        mThread = new Thread( PacketSyncer.class.getSimpleName() ) {
            public void run() {
                runLoop();
            }
        };
        
        mThread.setDaemon( true );
        mThread.start();
    }
    
    
    
    public synchronized <T> Sink<Packet> openStream(Sink<T> sink) {
        if(mClosed)
            return null;
        
        Pipe pipe = new Pipe(sink);
        mPipes.add(pipe);
        return pipe;
    }
    

    
    private void runLoop() {
        while( true ) {
            Command c = null;
            
            synchronized( this ) {
                if( mClosed ) {
                    return;
                }
                
                if( mQueue.isEmpty() ) {
                    try {
                        wait();
                    }catch( InterruptedException ex ) {}
                    
                    continue;
                }

                c = mQueue.first();
                long t = c.mMicros;
                
                if( t > Long.MIN_VALUE ) {
                    long waitMicros = mClock.toMasterMicros(t) - mClock.masterMicros();
                    
                    if( waitMicros > 10000L ) {
                        try {
                            wait( waitMicros / 1000L );
                        }catch( InterruptedException ex ) {}
                        
                        continue;
                    }
                }
                
                mQueue.remove( c );
            }
            
            c.run();
        }
    }
    
    
    
    private synchronized void scheduleSend( Pipe pipe, Packet packet ) {
        Command c = new SendCommand(mCommandSequence++, packet.getStartMicros(), pipe, packet);
        mQueue.add(c);
        
        if(mQueue.size() == 1 || mQueue.first() == c) {
            notifyAll();
        }
    }
    
    
    private synchronized void scheduleClear( Pipe pipe ) {
        Iterator<Command> iter = mQueue.iterator();
        boolean first = true;
        
        while(iter.hasNext()) {
            Command c = iter.next();
             
            if(c.mPipe == pipe) {
                switch(c.mCode) {
                case COMMAND_CLEAR:
                case COMMAND_CLOSE:
                    //No point in sending another clear.
                    return;

                case COMMAND_SEND:
                    iter.remove();
                    c.cancel();
                    
                    if(first) {
                        //Head is changing.
                        notifyAll();
                    }
                }
            }
            
            first = false;
        }
        
        mQueue.add(new ClearCommand(mCommandSequence++, pipe));
    }
    
    
    private synchronized void scheduleClose( Pipe pipe ) {
        Iterator<Command> iter = mQueue.iterator();
        boolean first = true;
        
        while(iter.hasNext()) {
            Command c = iter.next();
             
            if(c.mPipe == pipe) {
                if(c.mCode == COMMAND_CLOSE) {
                    //No point in sending another close.
                    return;
                }
                
                iter.remove();
                c.cancel();
                
                if(first) {
                    //Head is changing.
                    notifyAll();
                }
                
                first = false;
            }
        }
        
        mQueue.add(new CloseCommand(mCommandSequence++, pipe));
    }
    
    
    private synchronized void doNotify() {
        notifyAll();
    }
    
    

    @SuppressWarnings( "rawtypes" )
    private final class Pipe implements Sink<Packet> {

        private final Sink mSink;
        private final int mMaxCap;
        private boolean mClosed = false;
        private int mQueued = 0;
        
        
        Pipe(Sink sink) {
            mSink   = sink;
            mMaxCap = 5;
        }
        
        
        public void consume(Packet packet) throws IOException {
            synchronized(PacketSyncer.this) {
                while(mQueued >= mMaxCap && !mClosed) {
                    try {
                        PacketSyncer.this.wait();
                    }catch(InterruptedException ex) {
                        InterruptedIOException t = new InterruptedIOException();
                        t.initCause(ex);
                        throw t;
                    }
                }
                
                if(mClosed)
                    return;
                
                scheduleSend(this, packet);
                mQueued++;
            }
        }

        public void clear() {
            synchronized(PacketSyncer.this) {
                if(mClosed)
                    return;
                
                scheduleClear(this);
                mQueued = 0;
                PacketSyncer.this.notifyAll();
                //System.out.println("Clearing...");
            }
        }

        public void close() throws IOException {
            synchronized(PacketSyncer.this) {
                if(mClosed)
                    return;
                
                scheduleClose(this);
                mQueued = 0;
                PacketSyncer.this.notifyAll();
            }
        }
        
        public void decrementQueue() {
            synchronized(PacketSyncer.this) {
                mQueued = Math.max(0, mQueued - 1);
                PacketSyncer.this.notifyAll();
            }
        }
        
    }
    
    
    
    private final class PlayHandler implements PlayControl {

        public void playStart(long execMicros) {
            doNotify();
        }

        public void playStop(long execMicros) {
            doNotify();
        }

        public void seek(long execMicros, long gotoMicros) {
            doNotify();
        }

        public void setRate(long execMicros, double rate) {
            doNotify();
        }
        
    }

    
    
    
    private abstract class Command implements Runnable, Comparable<Command> {
        
        final long mSequence;
        final long mMicros;
        final int mCode;
        final Pipe mPipe;
        
        Command(long sequence, long micros, int code, Pipe pipe) {
            mSequence = sequence;
            mMicros   = micros;
            mCode     = code;
            mPipe     = pipe;
        }
        
        
        public abstract void run();
        
        
        public abstract void cancel();
        
        
        public int compareTo(Command c) {
            if(this == c)
                return 0;
            
            if(mMicros != c.mMicros)
                return mMicros < c.mMicros ? -1 : 1;
            
            return mSequence < c.mSequence ? -1: 1;
        }
        
    }
    
    
    private class SendCommand extends Command {
        
        private Packet mPacket;
        
        
        SendCommand(long sequence, long micros, Pipe pipe, Packet packet) {
            super(sequence, micros, COMMAND_SEND, pipe);
            mPacket = packet;
            packet.ref();
        }
        
        
        @SuppressWarnings("unchecked")
        public void run() {
            try {
                mPipe.mSink.consume(mPacket);
            }catch(IOException ex) {
                sLog.log(Level.WARNING, "Stream error.", ex);
                scheduleClose(mPipe);
            }finally{
                cancel();
            }
        }
        
        
        public void cancel() {
            if(mPacket != null) {
                mPacket.deref();
                mPacket = null;
            }
            
            mPipe.decrementQueue();
        }
        
    }

    
    private class ClearCommand extends Command {
        
        
        ClearCommand(long sequence, Pipe pipe) {
            super(sequence, Long.MIN_VALUE, COMMAND_CLEAR, pipe);
        }
        
        
        public void run() {
            mPipe.mSink.clear();
        }
        
        public void cancel() {}
        
    }
    
    
    private class CloseCommand extends Command {
        
        CloseCommand(long sequence, Pipe pipe) {
            super(sequence, Long.MIN_VALUE, COMMAND_CLOSE, pipe);
        }

        
        public void run() {
            try {
                mPipe.mSink.close();
            }catch(IOException ex) {
                sLog.log(Level.WARNING, "Failed to close stream.", ex);
            }
            
            synchronized(PacketSyncer.this) {
                mPipes.remove(mPipe);
            }
        }

        public void cancel() {}
        
    }
    
    
}
