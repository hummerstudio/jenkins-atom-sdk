package com.jenkinschina.atom.sdk;

import java.nio.file.Files;
import java.nio.file.Path;

@AtomService(value = "echo", paramClass = DemoParam.class)
public class EchoTask implements AtomTask {

    @Override
    public AtomResult run(AtomContext context) throws Exception {
        DemoParam param = context.getParam(DemoParam.class);
        String secret = context.getSecret("token");
        context.log("msg=" + param.msg + " times=" + param.times + " secretPresent=" + (secret != null));

        Path out = context.getWorkspace().resolve("out");
        Files.createDirectories(out);
        Files.writeString(out.resolve("artifact.txt"), "artifact:" + param.msg);
        Path report = out.resolve("report");
        Files.createDirectories(report);
        Files.writeString(report.resolve("index.html"), "<html><body>ok</body></html>");

        return AtomResult.success("done")
                .data("echo", param.msg)
                .sensitiveData("tokenOut", secret)
                .artifact("files", out.resolve("artifact.txt").toString())
                .report("report", "测试报告", report.toString(), "index.html");
    }
}
