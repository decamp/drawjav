/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

/**
 * @author Philip DeCamp
 */
public interface MemoryManager {
    public PacketAllocator<DrawPacket> allocator( StreamFormat stream );
}
