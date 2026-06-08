Artificial intelligence is a broad field that covers any technique enabling machines to perform tasks that normally require human intelligence. It ranges from rule based systems and classic machine learning to computer vision and robotics.

<!-- TODO adjust to have images pushed to assets on releases and link to them -->
![AI](https://raw.githubusercontent.com/spring-academy/course-maisey-spring-ai-intro/refs/heads/main/metadata/lms/01-overview/assets/ai.png)

Within that broad field sit several nested layers. **Machine Learning (ML)** is the subset of AI where algorithms learn from data to make predictions instead of being explicitly programmed. **Deep Learning** is a further subset of ML that uses neural networks, loosely inspired by how neurons in the brain work, to learn complex patterns. And **Generative AI** is the part of deep learning that does not just analyze or classify existing data but creates new content such as text, images, audio, and code.

Generative AI is one specific subset of artificial intelligence, yet it is the part that has captured the world's attention. When people talk about "AI" today, they almost always mean Generative AI, and most often the **Large Language Models (LLMs)** that power it.

## Why Is This Happening Now?

The ideas behind neural networks have existed for decades, so it is fair to ask why Generative AI suddenly feels like it is everywhere. Three forces came together at the same time.

The first was a breakthrough in architecture. In 2017, researchers at Google published the paper "Attention Is All You Need", which introduced the **transformer** architecture. Its core idea, the **attention mechanism**, lets a model weigh how strongly each word in a sequence relates to every other word. This captures context far better than earlier approaches, and just as importantly it does so in a way that scales well on modern hardware.

The second force was scale. Researchers found that making these models dramatically larger, and training them on more data with more computing power, kept producing better results. Capability grew with size in a way few people expected.

The third force was accessibility. The public release of capable chat based assistants showed everyone, not just researchers, what these systems could do. That moment triggered the current wave of investment, tooling, and adoption that we are living through now.

## How LLMs Work

<!-- TODO adjust to have images pushed to assets on releases and link to them -->
![How LLMs work](https://raw.githubusercontent.com/spring-academy/course-maisey-spring-ai-intro/refs/heads/main/metadata/lms/01-overview/assets/how-llms-work.png)

Despite their sophistication, LLMs do something conceptually simple. Given a piece of text, they predict the most likely next piece of text, and then they repeat that step over and over. Scaled up across billions of examples, this prediction produces responses that are coherent, contextually relevant, and often surprisingly creative.

A handful of terms describe how this works in practice and will appear throughout the course.

The text you send to a model is called the **prompt**. It is your instruction and context combined, and its quality has a large effect on the quality of the response. The text the model sends back is the **completion**.

Models do not read characters or whole words directly. They first break text into **tokens**, which are small chunks that can be a whole word, part of a word, or a piece of punctuation. Tokens are used because they strike a balance between characters, which would make sequences far too long, and whole words, of which there are too many across languages, so subword tokens let a model cover any text efficiently with a manageable vocabulary. As a rough guide, a short request like "Tell me about Spring AI" is around five tokens, while a hundred word answer is around one hundred and thirty tokens. You can see how text is split for yourself with the [OpenAI Tokenizer](https://platform.openai.com/tokenizer). Tokens matter for two practical reasons. They define how much a model can handle at once, and they are the currency of AI. When you use a hosted provider, you pay per token for both what you send and what you receive, so the number of tokens directly drives your cost. When you run a model on-premises, you no longer pay per token, but the cost does not disappear. It shifts to the compute you have to provide, since longer inputs and outputs mean more processing on your own hardware.

That capacity is the **context window**, the maximum amount of text, measured in tokens, that a model can consider in a single request. It includes both your prompt and the model's completion, so a larger context window lets the model take more information into account, although it also costs more.

Modern context windows are often hundreds of thousands of tokens, which sounds enormous compared to the small examples above. In practice, though, a good portion of that space is already reserved before your own input arrives. AI providers fill part of the window with system instructions that guide the model's behavior and with guardrails that keep it safe, and in real applications you will add your own instructions, conversation history, and retrieved context on top. 

The model's underlying capability comes from its **parameters**, the internal values it learns during training. Modern models have billions of them, and more parameters generally mean a more capable, but more expensive, model.

## Limitations of LLMs

For all their power, LLMs have real limitations, and understanding them is essential before you build anything serious.

They can **hallucinate**, which means producing confident answers that are simply wrong, because they predict plausible text rather than retrieve verified facts.

Their knowledge is **frozen at training time**. A model is unaware of anything that happened after its training cutoff, and it has no knowledge of your private or company specific data.

They are also **stateless**. A model has no memory of previous interactions unless you resend that history with every request, and how much history you can resend is limited by the context window.

On their own, LLMs **cannot take actions** in the outside world. They cannot query a database, call an API, or send an email without help.

And because they are **probabilistic**, the same prompt can produce different answers on different runs, which makes consistency and testing harder than in traditional software.

## Where Spring AI Fits In

Much of the recent progress in applied AI is about mitigating exactly these limitations, and the patterns involved are maturing quickly. 

Spring AI's capabilities are built around making these patterns practical for Java developers. By the end of this course, you can build applications that go well beyond what a raw LLM can do on its own.
