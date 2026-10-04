/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera;

import org.jline.prompt.Prompter;
import org.jline.prompt.PrompterFactory;
import org.jline.prompt.PromptBuilder;
import org.jline.prompt.PromptResult;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.util.Map;
import org.jline.prompt.Prompt;

public class MenuExample
{

    enum Action
    {
        OPEN, NEW, SETTINGS, KEYS, EXIT
    }

    public static void main(String[] args) throws Exception
    {
        try (Terminal terminal = TerminalBuilder.builder().build())
        {

            Prompter prompter = PrompterFactory.create(terminal);
            PromptBuilder promptBuilder = prompter.newBuilder();

            promptBuilder.createListPrompt()
                    .name("action")
                    .message("Select action")
                    .newItem(Action.OPEN.name()).text("📂 Open project").add()
                    .newItem(Action.NEW.name()).text("📄 New project").add()
                    .newItem(Action.SETTINGS.name()).text("⚙ Settings").add()
                    .newItem(Action.KEYS.name()).text("🔑 Manage keys").add()
                    .newItem(Action.EXIT.name()).text("🚪 Exit").add()
                    .addPrompt();

            Map<String, ? extends PromptResult<? extends Prompt>> result = prompter.prompt(null, promptBuilder.build());

            Action action = Action.valueOf(result.get("action").getResult());

            System.out.println("Elegido: " + action);
        }
    }
}
