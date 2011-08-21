object automata {
  type Automaton = (String, Set[String], Map[String, Map[Char, String]])

  val cadr = ("init", Set("end"), Map(
    "init" -> Map('c' -> "more"),
    "more" -> Map('a' -> "more", 'd' -> "more", 'r' -> "end"),
    "end" -> Map[Char, String]()
  ))

  val c_ad_r = ("init", Set("end"), Map(
    "init" -> Map('c' -> "req"),
    "req" -> Map('a' -> "more", 'd' -> "more"),
    "more" -> Map('a' -> "more", 'd' -> "more", 'r' -> "end"),
    "end" -> Map[Char, String]()
  ))

  def run(automaton: Automaton)(input: String): Boolean = {
    val (init, finals, map) = automaton
    def next(state: String, index: Int): Boolean = {
      if (index == input.length) 
	finals.contains(state)
      else
	map.get(state).flatMap(_.get(input(index))) match {
	  case None => false
	  case Some(new_state) => next(new_state, index + 1)
	}
    }
    next(init, 0)
  }

  import cafebabe._
  import AbstractByteCodes._
  import ByteCodes._

  // Creates a JVM program which prints true or false according to
  // whether its first argument is recognized by the given automaton
  def compile(className: String, automaton: Automaton): Unit = {
    val classFile = new ClassFile(className, None)
    val ch = classFile.addMainMethod.codeHandler
    val input = ch.getFreshVar
    val index = ch.getFreshVar
    ch << GetStatic("java/lang/System", "out", "Ljava/io/PrintStream;")
    ch << ALoad(0)
    ch << Ldc(0)
    ch << AALOAD
    ch << AStore(input)
    ch << Ldc(0)
    ch << IStore(index)

    val (init, finals, map) = automaton
    ch << Goto(init)

    val reject = ch.getFreshLabel("reject")
    val accept = ch.getFreshLabel("accept")
    val done = ch.getFreshLabel("done")
    val inc = "##inc"

    ch << Label(reject) << Ldc(0) << Goto(done)
    ch << Label(accept) << Ldc(1) << Goto(done)

    for ((state, table) <- map) {
      ch << Label(state + inc)
      ch << ILoad(index) << Ldc(1) << IADD << IStore(index)
      ch << Label(state)
      ch << ALoad(input)
      ch << InvokeVirtual("java/lang/String", "length", "()I")
      ch << ILoad(index)
      ch << If_ICmpEq(if (finals.contains(state)) accept else reject)
      val inputChar = if (table.size == 0) None else {
	ch << ALoad(input) << ILoad(index)
	ch << InvokeVirtual("java/lang/String", "charAt", "(I)C")
	if (table.size == 1) None else {
	  val tempVar = ch.getFreshVar
	  ch << IStore(tempVar)
	  Some(tempVar)
	}
      }
      for ((char, new_state) <- table) {
	inputChar.foreach(ch << ILoad(_))
	ch << Ldc(char)
	ch << If_ICmpEq(new_state + inc)
      }
      inputChar.foreach(ch.freeVar(_))
      ch << Goto(reject)
    }

    ch << Label(done)
    ch << InvokeVirtual("java/io/PrintStream", "println", "(Z)V")
    ch << RETURN
    ch.freeze
    classFile.writeToFile(className + ".class")
  }
}
