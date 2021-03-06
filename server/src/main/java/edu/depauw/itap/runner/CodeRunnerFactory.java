package edu.depauw.itap.runner;

import edu.depauw.itap.compiler.CompilerService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class CodeRunnerFactory {

  @Autowired
  private Clock clock;

  public CodeRunner createCodeRunner(String session, MessageHeaders messageHeaders,
      CompilerService compilerService, SimpMessagingTemplate messagingTemplate) {
    return new CodeRunnerImpl(session, messageHeaders, compilerService, messagingTemplate, clock);
  }
}
