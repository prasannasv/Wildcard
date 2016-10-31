import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
//import javax.annotation.Nonnull;

class Solution {
  public boolean isMatch(String s, String p) {
    return new WildcardMatcher(p).isMatch(s);
  }
}
/**
 * The basic idea is to create an NFA out of the pattern and then
 * walk that NFA with the given input and see if we reach a final state.
 */
public class WildcardMatcher {
  private final String pattern;
  private final WildcardNfa nfa;

  public WildcardMatcher(final String pattern) {
    this.pattern = pattern;
    this.nfa = new WildcardNfa(pattern);
    System.out.println("nfa: " + nfa);
  }

  public boolean isMatch(final String s) {
    return nfa.walk(s).stream().filter(state -> state.isFinal()).findAny().isPresent();
  }

  public static void main(final String[] args) {
    final String[] patterns = {
      "a",
      "ab",
      "a?",
      "a*",
      "?",
      "a*b",
      "?*",
      "*?",
      "",
      "*",
    };

    /*for (final String pattern : patterns) {
      final WildcardMatcher matcher = new WildcardMatcher(pattern);
      System.out.println("pattern: " + pattern + ", nfa: " + matcher.nfa);
    }*/

    final String[][] tests = {
      {"ab", "ab"},
      {"a*", "a"},
      {"a?", "aa"},
      {"a?b", "aac"},
      {"a*b*c", "aababvc"},
      {"*", ""},
      {"", ""},
      {"**ho", "ho"},
      {"a*****a", "aababbabaaaababaaaba"},
      {"bb*a*bbbb**ab***b**aba*aa**b*a*ab*aa**baaaab***ab*a*****bb*ab*a*ab****a**ab**a*a***bab*b**b*bb***abbabb",
      "bbaaaabababaaabaabbabaabababaaabbaaaababbbbbbbbbaabbaababbaababbabbaabbbabababbababbaaaabaababaabbababbaabbabaaabaabaabaabbabbaaaababaaaabababbbbbabbabbbababbabbabbabbabbbbababaabaaababbaaabaabbbbbaaa"},
    };

    for (final String[] test : tests) {
      final WildcardMatcher matcher = new WildcardMatcher(test[0]);
      System.out.println("pattern: " + test[0] + ", input: " + test[1] + ", isMatch: " + matcher.isMatch(test[1]));
    }
  }
}

class WildcardNfa {
  private final List<State> states = new LinkedList<>();

  public WildcardNfa(final String pattern) {
    int stateId = 0;
    final State start = createState(stateId++);
    State prev = start;

    final char[] p = pattern.toCharArray();
    for (int i = 0; i < p.length; ++i) {
      final char curr = p[i];
      if (i > 0 && p[i - 1] == '*' && curr == '*') {
        continue;
      }
      final State next = createState(stateId++);

      if (curr == '?') {
        prev.addTransition(new Transition(Transition.Type.ANY, curr, next));
      } else if (curr == '*') {
        prev.addTransition(new Transition(Transition.Type.ANY, curr, prev));
        prev.addTransition(new Transition(Transition.Type.LAMBDA, '~', next));
      } else {
        prev.addTransition(new Transition(Transition.Type.REGULAR, curr, next));
      }

      prev = next;
    }

    prev.markFinal();
  }

  private State createState(final int id) {
    final State newState = new State(id);
    states.add(newState);
    return newState;
  }

  public List<State> walk(final String str) {
    List<State> currStates = new LinkedList<>();
    currStates.add(states.get(0));
    currStates.addAll(states.get(0).getLambdaTransitionStates());
    List<State> nextStates = new LinkedList<>();
    nextStates.addAll(currStates);
    for (final char c : str.toCharArray()) {
      nextStates = new LinkedList<>();
      final Set<Integer> addedStateIds = new HashSet<>();
      System.out.println("currStates: " + currStates + ", nextStates: " + nextStates);
      for (final State curr : currStates) {
        for (final State potential : curr.getNextStates(c)) {
          if (!addedStateIds.contains(potential.getId())) {
            addedStateIds.add(potential.getId());
            nextStates.add(potential);
          }
        }
      }
      currStates = nextStates;
      System.out.println("currStates: " + currStates + ", nextStates: " + nextStates);
    }

    System.out.println("nextStates: " + nextStates);
    return nextStates;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();

    for (final State state : states) {
      sb.append(state.toString());
    }

    return sb.toString();
  }
}

class State {
  private final int id;
  private final List<Transition> transitions = new LinkedList<>();
  private boolean isFinal;

  public State(final int id) {
    this.id = id;
  }

  public State addTransition(final Transition t) {
    transitions.add(t);
    return this;
  }

  public int getId() {
    return id;
  }

  public void markFinal() {
    isFinal = true;
  }

  public boolean isFinal() {
    return isFinal;
  }

  /** lambda transition states - recursively */
  public List<State> getLambdaTransitionStates() {
    final List<State> reachableStates = transitions
      .stream()
      .filter(t -> t.isLambdaTransition())
      .map(t -> t.getNext())
      .collect(Collectors.toList());

    List<State> prevRun = new LinkedList<>();
    prevRun.addAll(reachableStates);
    while (true) {
      List<State> currRun = prevRun.stream().flatMap(s -> s.getLambdaTransitionStates().stream()).collect(Collectors.toList());
      if (currRun.isEmpty()) {
        break;
      }
      reachableStates.addAll(currRun);
      prevRun = currRun;
    }

    return reachableStates;
  }

  public List<State> getStates() {
    final List<State> states = new LinkedList<>();
    states.add(this);
    states.addAll(getLambdaTransitionStates());
    return states;
  }

  public List<State> getNextStates(final char c) {
    final List<State> states = new LinkedList<>();

    states.addAll(getLambdaTransitionStates());
    final Set<Integer> addedStateIds =
      states
      .stream()
      .mapToInt(s -> s.id)
      .boxed()
      .collect(Collectors.toSet());

    states.addAll(transitions
      .stream()
      .filter(t -> t.isMatch(c))
      .map(t -> t.getNext())
      .flatMap(s -> s.getStates().stream())
      .filter(s -> !addedStateIds.contains(s.id))
      .distinct() // It's okay not to implement equals
      .collect(Collectors.toList()));

    System.out.println("from: " + this + ", on char: " + c + ", nextStates: " + states);
    return states;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();

    sb.append('(');
    if (isFinal) {
      sb.append('(');
    }
    sb.append(id);
    if (isFinal) {
      sb.append(')');
    }
    sb.append(')');

    sb.append(' ');

    for (final Transition edge : transitions) {
      sb.append(edge);
      sb.append(' ');
    }

    return sb.toString();
  }
}

class Transition {
  enum Type {
    ANY,
    LAMBDA,
    REGULAR,
  }

  private final Type type;
  private final char label;
  private final State next;

  public Transition(final Type type, final char label, final State next) {
    this.type = type;
    this.label = label;
    this.next = next;
  }

  public boolean isLambdaTransition() {
    return type == Type.LAMBDA;
  }

  public boolean isMatch(final char c) {
    return type == Type.ANY || type == Type.LAMBDA || label == c;
  }

  public State getNext() {
    return next;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();

    sb.append('/');
    sb.append(label);
    if (type == Type.ANY && label == '*') {
      sb.append('<');
    } else {
      sb.append('>');
    }

    return sb.toString();
  }
}
