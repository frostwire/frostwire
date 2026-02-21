# Mining Git History to Build Developer Agent Personas

*A new software engineering practice for the age of agentic teams — and an honest look at what it costs*

---

There is a new kind of software team forming inside repositories everywhere. It is not made of humans alone. It is made of humans and AI agents working together — agents that browse code, write tests, fix bugs, open pull requests, and respond to feedback at any hour. The practice of configuring and directing these agents is itself becoming a software engineering discipline.

But most teams configure their agents as if they were generic tools. They write a single `AGENTS.md` or system prompt that says "use camelCase" and "always run tests before committing." That is table stakes. What comes next is more interesting — and more uncomfortable.

What if the agents on your team actually *think* like the senior engineers who built the codebase?

---

## The Exercise

A git log is one of the most honest documents a programmer ever produces. It records what a person noticed, what they fixed, how they explained themselves, and what they considered worth doing. Over thousands of commits spanning years of work, a developer's values, instincts, and style crystallize into the history.

The practice is simple to describe:

**Read a project's git history as an ethnographer would. Identify the major contributors. Extract their patterns. Write those patterns into agent persona files that an AI agent can load and reason from.**

The result is a file — typically placed at `.claude/agents/<handle>.md` or the equivalent in your agentic framework — that describes a developer not as a rulebook but as a character. Not "always use the logger" but "you are bothered by raw `e.printStackTrace()` calls — the logger is right there, and raw stack traces are production noise."

A rulebook tells an agent what to do. A persona tells it what kind of engineer it is — which means it makes correct decisions in situations the rulebook never anticipated.

---

## What the Git Log Actually Tells You

When you read commits looking for a person rather than a patch, different things become visible.

Consider two developers on the same long-lived project. One writes commit messages that function as post-mortems: the message contains the diagnosis, the failure mode, the reasoning behind the fix, and the test conditions. The other writes messages that are a single noun phrase — "avoid copy of peer info list" — and trusts the diff to speak. These are not two styles of writing. They are two philosophies of communication, two different theories of what a commit is *for*.

Or consider what kind of changes each person makes. One developer's refactors are net-positive on lines of code — new abstractions, new executors, new scoring algorithms. The other's refactors are net-negative — inner classes made `static final`, `public` fields made package-private, unnecessary list copies deleted, `printStackTrace()` calls removed. The first developer tends to *add* structure. The second tends to *remove* it.

Neither pattern is better. Both are coherent. And both can be taught to an agent — not through rules, but through characterization.

---

## The Process

### 1. Identify the major contributors

```bash
git log --all --format="%ae" | sort | uniq -c | sort -rn | head -10
```

Look for the two to five people who have shaped the codebase most deeply. These are the candidates. Focus on engineers who have made hundreds or thousands of commits, not dozens — you need enough signal to distinguish style from accident.

### 2. Sample commits across categories

Filter by author and sample across different types of work:

```bash
git log --all --author="<email>" --format="%H %s" | head -100
```

Then read full diffs for a selection of them:

```bash
git show <hash> -p
```

Sample from: bug fixes, refactors, new features, dependency updates, documentation changes, and test additions. Each category tends to reveal different facets of how a developer thinks.

### 3. Look for patterns across three dimensions

**Commit message style** — length, structure, use of rationale vs. bare description, whether they explain the *why* or just the *what*, whether they name what was wrong before they describe what they changed.

**Diff character** — do their changes grow the codebase or shrink it? Do they touch adjacent code or limit scope precisely? Do they tend toward new abstractions or toward simplification of existing ones? How often do they update tests? Documentation?

**Code style within the change** — naming conventions, exception handling posture, how they reach for data structures, how they handle null, whether they write comments and what kind, how they structure class visibility.

### 4. Write the persona in second person

The output should be a character description, not a style guide. Write it as if describing a person to another person:

> *"You are bothered by methods that take a wide object just to call two methods on a nested field. You refactor the signature to take the minimum required type, remove the transitive dependency, and clean the imports. You don't comment on this in the commit message — the diff says enough."*

That kind of description transfers. An agent that has internalized it will make decisions consistently in new situations — not because it looked up a rule, but because it knows who it is.

### 5. Place the file canonically

In Claude Code: `.claude/agents/<handle>.md`

In other frameworks, this is typically a system prompt file loaded at agent startup. The filename should be the developer's identifier — their GitHub handle, their name, whatever makes the reference clear.

---

## What Changes When Agents Have Personas

When you run an agentic team — multiple AI agents working concurrently on different parts of a codebase — the agents without personas are interchangeable. They apply whatever global rules exist and move on. They produce code that compiles, passes tests, and solves the stated problem. But it is generic code. It does not have the fingerprints of the codebase.

An agent running with a persona will, when it encounters a method that takes a full domain object just to reach a nested value, feel the friction and refactor the signature. Not because a rule said to. Because that is what this developer does.

An agent running with a different persona will, when it catches an exception from a parser and has to surface it to the user, write a message that makes sense to a human — add a defensive check before the operation that caused the crash, explain in a comment why the check is there, and write a commit message that teaches the next developer what the failure mode was.

These are not the same agent. They have different intuitions derived from different patterns. And when working together in an agentic team, they produce code that resembles the coherent output of a human team rather than the averaged-out output of a generic tool.

---

## The Software Dark Factory

The term "dark factory" comes from manufacturing: a fully automated production facility that needs no lights because no humans are present. Robots build things. Sensors monitor them. Nothing requires a person to be in the room.

The software equivalent is arriving. Not fully dark — humans still make architectural decisions, review output, and define goals — but increasingly, the routine work of software maintenance and feature development is being performed by agents that run continuously, pick up tasks, write code, and submit it for review. Some organizations are already measuring engineering output in terms of how much human time is consumed per shipped feature, with the goal of driving that number down.

Developer persona files are how you make a dark factory *coherent* rather than merely productive. They encode the judgment that makes a codebase readable — the accumulated instincts about what belongs in a commit message, when to copy a list and when not to, how to communicate a failure mode to a user — and distribute that judgment to agents that work without the original engineer present.

This is useful. It is also worth being honest about what it means.

---

## The Part Nobody Wants to Say

This practice will be used to replace people.

Not immediately, and not crudely. But the logic is clear: if you can extract a senior engineer's coding style and judgment into a persona file, and then run that persona at scale across an agentic team, the argument for keeping the senior engineer on payroll weakens. Not because their persona file *is* them — it isn't — but because it is close enough for most of the work that person was doing.

The engineers most at risk are not the weakest ones. They are the strongest — the ones with the most distinctive, most consistent, most teachable styles. The ones whose patterns show up clearly in the git log. The more legible you are as a developer, the more extractable your value is.

This is not an argument against doing the practice. It is going to happen regardless. But engineers who understand it early can position themselves differently. The value that is hardest to extract from a git log is:

- **Judgment under novel conditions** — situations the codebase has never seen before, where past patterns don't apply
- **Architectural intuition** — knowing what to build before knowing how to build it
- **Relationship with the product** — understanding what users actually need, which does not live in commit messages
- **The ability to say no** — to scope creep, to bad ideas, to technically correct but strategically wrong decisions

These are the capacities to develop. Not as a hedge against the persona file, but because they are what good engineering actually is when stripped of routine execution.

---

## Caveats and Honest Limitations

**The persona is a model, not the person.** An 80-line document cannot capture the full depth of an engineer's judgment. It is a useful caricature — not a replacement.

**Git history has selection bias.** You see what was committed, not what was argued about in review, not what was deleted before it shipped, not the hours of investigation before the one-line fix. The commit is the output; the thinking behind it is partially invisible.

**Style is not wisdom.** A developer's patterns are a proxy for their values, not identical to them. An agent that mimics a deletion-heavy refactoring style might delete something that should not be deleted. The persona needs to be paired with human review — especially on changes that are consequential.

**Personas go stale.** Engineers change. A developer who wrote code in 2018 with one philosophy may have different instincts in 2026. Treat the persona file as a living document, not a permanent artifact.

---

## Getting Started

You need three things:

**1. A git repository with meaningful history.** Real commit messages, written by actual people who cared about what they were saying. If your team uses squash-merge with auto-generated messages, the signal-to-noise ratio drops significantly. This is, incidentally, another argument for writing commit messages carefully — they compound into something valuable over time.

**2. An LLM capable of synthesis.** Any current frontier model can do this. Feed it thirty to fifty commits per author — full diffs, not just messages — and ask it to characterize the developer across multiple dimensions. The prompt can be simple:

> *"Read the following commits. Write a character study of this engineer — not a style guide, but a persona. What do they notice? What bothers them? What do they reach for first? What do they refuse to do? Write in second person so it can be used as an agent persona."*

**3. A canonical location in your project.** For Claude Code, `.claude/agents/<handle>.md`. For other frameworks, wherever system prompts for named agents live. The filename should reference the developer unambiguously.

Review the output against your own knowledge of the person. Correct what is wrong. Add what is missing — especially the tacit knowledge that never made it into a commit message. Commit the file as you would any other team configuration.

---

## Closing Thought

Software has always been a human artifact — shaped not just by requirements but by the people who made it. The architecture of a system reflects the mental models of its architects. The idioms in the codebase reflect the habits of whoever wrote the most of it. The comments reflect what the authors thought was worth saying.

That human fingerprint is what makes a codebase coherent rather than merely functional. It is what lets a new developer read old code and understand not just what it does but why it does it that way.

If we are building agentic teams that maintain and extend these codebases, one of the most important things we can do is give those agents something like taste — derived not from abstraction but from evidence. The git log is that evidence. It has been accumulating for years.

The dark factory does not have to be anonymous. The machines can work in someone's style.

The question each engineer should be sitting with is: *what aspects of how I work cannot be captured from a log?* Those are the parts worth investing in.

---

*The author writes software and thinks about what software engineering becomes when the code mostly writes itself.*
