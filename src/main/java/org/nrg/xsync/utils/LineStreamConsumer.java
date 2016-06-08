package org.nrg.xsync.utils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LineStreamConsumer implements Runnable, Closeable {
    private final Logger logger = LoggerFactory.getLogger(LineStreamConsumer.class);
    private final InputStream in;
    private final ArrayList<String> outLines = new ArrayList<String>();

    public LineStreamConsumer(final InputStream in) {
        this.in = in;
    }

    /*
     * (non-Javadoc)
     * @see java.io.Closeable#close()
     */
    public void close() throws IOException {
        in.close();
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {
        try {
            IOException ioexception = null;
            try {
                final InputStreamReader isr = new InputStreamReader(in);
                final BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ( (line = br.readLine()) != null)
                    outLines.add(line);
            } catch (IOException e) {
                throw ioexception = e;
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    if (null == ioexception) {
                        throw e;
                    } else {
                        logger.error("error closing stream", e);
                        throw ioexception;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("error consuming stream", e);
        }
    }

    public LineStreamConsumer start() {
        new Thread(this).start();
        return this;
    }

    public List<String> getLines( ){
        return outLines;
    }

}
