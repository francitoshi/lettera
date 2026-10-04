/*
 * Copyright (C) 2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.email;

import java.io.IOException;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.util.Map;

public class EmailProviders
{

    public static Map<String, EmailPreset> load(InputStream input)
    {
        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(options);

        TypeDescription emailDesc = new TypeDescription(EmailPreset.class);
        emailDesc.addPropertyParameters("smtp", ServerSettings.class);
        emailDesc.addPropertyParameters("imap", ServerSettings.class);
        emailDesc.addPropertyParameters("pop3", ServerSettings.class);
        constructor.addTypeDescription(emailDesc);

        Yaml yaml = new Yaml(constructor);
        Map<String, Object> raw = yaml.load(input);

        Map<String, EmailPreset> result = new java.util.LinkedHashMap<>();
        Yaml entryYaml = new Yaml(constructor);
        raw.forEach((k, v) -> result.put(k, entryYaml.loadAs(new Yaml().dump(v), EmailPreset.class)));
        return result;
    }
    
    public static Map<String, EmailPreset> load()
    {
        try (InputStream in = EmailProviders.class.getResourceAsStream("email-providers.yml"))
        {
            return load(in);
        }
        catch (IOException ex)
        {
            System.getLogger(EmailProviders.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            return null;
        }
    }
}
