/*
 *  Config.java
 *
 *  Copyright (c) 2025 francitoshi@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Report bugs or new features to: francitoshi@gmail.com
 */
package io.francitoshi.lettera;

import io.nut.base.crypto.Kripto;
import io.nut.base.crypto.Rand;
import io.nut.base.encoding.Base64;
import io.nut.base.encoding.Base64DecoderException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *
 * @author franci
 */
public class Config
{
    private static final String ARGON2_PARALLELISM = "argon2.parallelism";
    private static final String ARGON2_MEMORY_KB = "argon2.memory_kb";
    private static final String ARGON2_ITERATIONS = "argon2.iterations";
    private static final String ARGON2_SALT_MASTER = "argon2.salt.master";
    
    private static final int ITERATIONS = 26;
    private static final int MEM_KBSIZE = 65536;
    private static final int PARALELISM = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    
    private static final Rand RAND = Kripto.getRand();
    
    private final byte[] salt;
    public final int iterations;
    public final int memoryKB;
    public final int parallelism;

    public Config(byte[] salt, int iterations, int memoryKB, int parallelism)
    {
        this.salt = salt;
        this.iterations = iterations;
        this.memoryKB = memoryKB;
        this.parallelism = parallelism;
    }

    public byte[] getSalt()
    {
        return salt.clone();
    }
    
    public static Config load(File configProperties) throws IOException, Base64DecoderException
    {
        if(configProperties.exists())
        {
            Properties properties = new Properties();
    
            properties.load(new FileInputStream(configProperties));
            
            byte[] salt = Base64.decode(properties.getProperty(ARGON2_SALT_MASTER));
            int iterations = Integer.parseInt(properties.getProperty(ARGON2_ITERATIONS, Integer.toString(ITERATIONS)));
            int memoryKB = Integer.parseInt(properties.getProperty(ARGON2_MEMORY_KB,Integer.toString(MEM_KBSIZE)));
            int parallelism = Integer.parseInt(properties.getProperty(ARGON2_PARALLELISM,Integer.toString(PARALELISM)));
            
            return new Config(salt, iterations, memoryKB, parallelism);
        }
        return null;
    }
    public static Config createDefault(File configProperties) throws IOException, Base64DecoderException
    {
        byte[] salt     = RAND.nextBytes(new byte[32]);
        int iterations  = ITERATIONS;
        int memoryKB    = MEM_KBSIZE;
        int parallelism = PARALELISM;
        Properties properties = new Properties();
        
        
        properties.setProperty(ARGON2_SALT_MASTER, Base64.encode(salt));
        properties.setProperty(ARGON2_ITERATIONS, Integer.toString(iterations));
        properties.setProperty(ARGON2_MEMORY_KB, Integer.toString(memoryKB));
        properties.setProperty(ARGON2_PARALLELISM,Integer.toString(parallelism));
        properties.store(new FileOutputStream(configProperties),"created with default values by lettera");
        
        return new Config(salt, iterations, memoryKB, parallelism);
    }
}
