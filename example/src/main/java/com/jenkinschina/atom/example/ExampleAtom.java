package com.jenkinschina.atom.example;

import com.jenkinschina.atom.sdk.AtomContext;
import com.jenkinschina.atom.sdk.AtomResult;
import com.jenkinschina.atom.sdk.AtomService;
import com.jenkinschina.atom.sdk.AtomTask;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reference atom: greeting + optional secret + one artifact + one report. */
@AtomService(value = "example-greeter", paramClass = ExampleParam.class)
public class ExampleAtom implements AtomTask {

    @Override
    public AtomResult run(AtomContext context) throws Exception {
        ExampleParam param = context.getParam(ExampleParam.class);
        String msg = param.msg == null ? "hello" : param.msg;
        String token = context.getSecret("deployToken");

        context.group("greeting");
        for (int i = 0; i < Math.max(1, param.count); i++) {
            context.log("[" + (i + 1) + "] " + msg);
        }
        if (token != null) {
            // printing a secret on purpose: the runner must mask it in the console
            context.log("token=" + token);
        }

        Path out = context.getWorkspace().resolve("out");
        Files.createDirectories(out.resolve("report"));
        Files.writeString(out.resolve("greeting.txt"), msg + System.lineSeparator());
        Files.writeString(out.resolve("report/index.html"), "<html><body><h1>" + msg + "</h1></body></html>");

        return AtomResult.success("greeted")
                .data("greeting", msg)
                .artifact("files", out.resolve("greeting.txt").toString())
                .report("report", "问候报告", out.resolve("report").toString(), "index.html");
    }
}
