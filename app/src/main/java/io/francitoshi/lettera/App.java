package io.francitoshi.lettera;

import io.nut.base.crypto.gpg.PASS;
import io.nut.base.util.Utils;
import io.nut.core.net.mail.IMAP;
import io.nut.core.net.mail.SMTP;
import io.nut.core.net.mail.POP3;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;

public class App
{
    public static void main1(String[] args)
    {
        try
        {
            // 1. Insertar dos claves
            System.out.println("Insertando claves...");
            PASS.setKey("clave1", "1111---");
            PASS.setKey("clave2", "2222---");
            PASS.setKey("clave3", "3333---");

            // 2. Listar claves con sus valores
            System.out.println("\nListando claves y valores:");
            for (String key : PASS.listKeys())
            {
                String value = PASS.getKey(key);
                System.out.println("Clave: " + key + ", Valor: " + value);
            }

            // 3. Modificar una clave
            System.out.println("\nModificando clave1...");
            PASS.setKey("clave1", "9999");

            // 4. Listar claves con sus valores nuevamente
            System.out.println("\nListando claves y valores después de modificar:");
            for (String key : PASS.listKeys())
            {
                String value = PASS.getKey(key);
                System.out.println("Clave: " + key + ", Valor: " + value);
            }

        }
        catch (IOException e)
        {
            System.err.println("Error: " + e.getMessage());
        }
    }

}
