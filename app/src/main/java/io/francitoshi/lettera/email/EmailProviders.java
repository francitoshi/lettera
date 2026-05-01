/*
 *  EmailProviders.java
 *
 *  Copyright (c) 2026 francitoshi@gmail.com
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
package io.francitoshi.lettera.email;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.util.Map;

public class EmailProviders
{

    public static Map<String, EmailSettings> load(InputStream input)
    {
        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(options);

        TypeDescription emailDesc = new TypeDescription(EmailSettings.class);
        emailDesc.addPropertyParameters("smtp", ServerSettings.class);
        emailDesc.addPropertyParameters("imap", ServerSettings.class);
        emailDesc.addPropertyParameters("pop3", ServerSettings.class);
        constructor.addTypeDescription(emailDesc);

        Yaml yaml = new Yaml(constructor);
        Map<String, Object> raw = yaml.load(input);

        Map<String, EmailSettings> result = new java.util.LinkedHashMap<>();
        Yaml entryYaml = new Yaml(constructor);
        raw.forEach((k, v) -> result.put(k, entryYaml.loadAs(new Yaml().dump(v), EmailSettings.class)));
        return result;
    }
    
    public static Map<String, EmailSettings> load() throws Exception
    {
        try (InputStream in = EmailProviders.class.getResourceAsStream("email-providers.yml"))
        {
            return load(in);
        }
    }
}
