/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;
import java.util.*;

public class IOExceptionList extends IOException {

    private final List<Throwable> mList = new LinkedList<Throwable>();
    
    
    public IOExceptionList() {}


    public IOExceptionList( String msg ) {
        super( msg );
    }


    public IOExceptionList( String msg, Throwable firstCause ) {
        super( msg );
        mList.add( firstCause );
    }


    public IOExceptionList( Throwable firstCause ) {
        mList.add( firstCause );
    }
    
    
    @Override
    public Throwable initCause( Throwable t ) {
        addCause( t );
        return this;
    }


    public void addCause( Throwable ex ) {
        if( mList.isEmpty() ) {
            initCause( ex );
        }
        mList.add( ex );
    }


    public int causeCount() {
        return mList.size();
    }


    public Throwable getCause( int idx ) {
        return mList.get( idx );
    }


    public List<Throwable> getCauses() {
        return mList;
    }
    
    
    @Override
    public void printStackTrace() {
        printStackTrace( System.err );
    }
    
    @Override
    public void printStackTrace( PrintWriter out ) {
        super.printStackTrace( out );
        int len = mList.size();
        for( int i = 0; i < len; i++ ) {
            out.format( "### Exception %d of %d ###\n", i, len );
            mList.get( i ).printStackTrace( out );
        }
    }
    
    @Override
    public void printStackTrace( PrintStream out ) {
        super.printStackTrace( out );
        int len = mList.size();
        for( int i = 0; i < len; i++ ) {
            out.format( "### Exception %d of %d ###\n", i, len );
            mList.get( i ).printStackTrace( out );
        }
    }

    
    
    public static IOException join( IOException err, IOException newErr ) {
        return join( null, err, newErr );
    }


    public static IOException join( String msg, IOException err, IOException newErr ) {
        if( err == null ) {
            return newErr;
        }
        if( newErr == null ) {
            return err;
        }
        if( err instanceof IOExceptionList ) {
            ((IOExceptionList)err).addCause( newErr );
            return err;
        }
        
        IOExceptionList ret;
        if( msg == null ) {
            ret = new IOExceptionList( msg, err );
        } else {
            ret = new IOExceptionList( err );
        }
        ret.addCause( newErr );
        
        return ret;
    }
    
}
