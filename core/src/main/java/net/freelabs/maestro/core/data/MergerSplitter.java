/*
 * Copyright (C) 2015-2016 Dionysis Lappas <dio@freelabs.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.freelabs.maestro.core.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides methods to split files into chuck of bytes and merge them
 * back to files.
 */
public class MergerSplitter {
    /**
     * Size of chunk to split files in bytes.
     */
    private final int CHUNK_SIZE;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(MergerSplitter.class);
    /**
     * 
     * @param CHUNK_SIZE size of chunk to split file in BYTES.
     */
    public MergerSplitter(int CHUNK_SIZE) {
        this.CHUNK_SIZE = CHUNK_SIZE;
    }
    /**
     * Splits a files into chunks of bytes.
     * @param filePath the path of file to split.
     * @return list of byte chunks from the split file.
     * @throws IOException in case of I/O error.
     */
    public List<byte[]> splitFile(String filePath) throws IOException {
        List<byte[]> chunkList = new ArrayList<>();
        // read file into a byte array
        byte[] byteArr = Files.readAllBytes(Paths.get(filePath));

        int lastByteIndx = 0;
        for (int firstByteIndx = 0; firstByteIndx + CHUNK_SIZE <= byteArr.length; firstByteIndx += CHUNK_SIZE) {
            // read CHUNK_SIZE bytes and save to chunk
            lastByteIndx = firstByteIndx + CHUNK_SIZE;
            byte[] chunk = Arrays.copyOfRange(byteArr, firstByteIndx, lastByteIndx);
            // add to list
            chunkList.add(chunk);
        }
        // if byteArr.length/ARRAY_SIZE not even number then there are some
        // bytes left that were not included
        int remainingBytes = byteArr.length - lastByteIndx;
        if (remainingBytes != 0) {
            byte[] chunk = Arrays.copyOfRange(byteArr, lastByteIndx, byteArr.length);
            chunkList.add(chunk);
        }

        return chunkList;
    }
    /**
     * Merges a list of byte chunks into a file.
     * @param chunkList list of byte chunks from file split.
     * @param filePath the path where the final merged file will be saved.
     * @throws IOException in case of I/O) error.
     */
    public void mergeFile(List<byte[]> chunkList, String filePath) throws IOException {
        int numOfChunks = chunkList.size();
        byte[] lastChunk = chunkList.get(numOfChunks - 1);
        int sizeOfLastChunk = lastChunk.length;

        // create merge byte array
        int mergeArrSize = (numOfChunks - 1) * CHUNK_SIZE + sizeOfLastChunk;
        byte[] merged = new byte[mergeArrSize];

        // merge chunks into a byte array
        for (int i = 0; i < chunkList.size(); i++) {
            // get chunk data
            byte[] chunk = chunkList.get(i);
            // get number of bytes to copy
            int length = chunk.length;
            // calc starting pos of destination array
            int destPos = i * CHUNK_SIZE;
            // copy #length bytes from chunk starting at 0 pos to merged starting at destPos
            System.arraycopy(chunk, 0, merged, destPos, length);
        }
        Files.write(Paths.get(filePath), merged, StandardOpenOption.CREATE);
    }
}
