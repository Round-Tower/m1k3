# Executive Strategy Report: Dynamic Delivery and Optimized Edge Deployment of Multi-Modal Large Language Models

## I. Executive Synthesis and Strategic Roadmap for Edge MLLMs

### 1.1. Strategic Imperative: The Edge Multi-Modal Opportunity

The transition from deploying solely text-based large language models (LLMs) to fully integrated Multi-Modal Language Models (MMLLMs) on edge devices represents a significant competitive opportunity. MMLLMs offer powerful visual comprehension capabilities, enabling analysis of text, charts, and layouts within images, understanding videos, and generating structured outputs directly on mobile devices.[1]

However, this shift introduces severe operational constraints regarding memory, computation, and energy consumption inherent to resource-limited mobile platforms.[2] The conventional approach of utilizing monolithic cloud deployment is increasingly insufficient due to concerns over high latency, data privacy risks, and unpredictable communication/usage costs.[3] Therefore, the required strategic response is the implementation of a **Local-Cloud Hybrid Architecture**. This architecture leverages dynamic asset delivery mechanisms combined with sophisticated inference offloading strategies to ensure efficient, high-quality service delivery. The primary objective is the successful deployment of compact MLLMs, specifically those under 7 billion parameters, which are deemed viable for high-utility mobile applications.[1]

### 1.2. Core Findings and Key Risks (Summary for Project Review)

| Insight/Finding | Strategic Implication | Critical Risk/Mitigation |
|----------------|----------------------|-------------------------|
| **Architectural Split:** The Language Model (LLM) component constitutes the overwhelming majority of the MLLM payload, accounting for approximately 80% of the total parameters (e.g., 7.7B parameters out of 9.7B in some architectures).[4] | **Mandates Dynamic Delivery:** The LLM weights must be segmented and delivered using dynamic modes, such as Fast-Follow and On-Demand, within the delivery mechanism (e.g., Play Asset Delivery, PAD) to minimize the initial application download size.[5] | **Mitigation:** The LLM payload must be fragmented based on utility. Core, frequently utilized layers should be prioritized for Fast-Follow delivery to mitigate user-facing latency spikes during the first multi-modal interaction. |
| **Optimization Nuance:** Simple weight-only quantization effectively reduces model size [6] but often fails to significantly improve inference latency or energy efficiency due to the necessity of costly on-the-fly weight dequantization during runtime.[2] | **Mandates Hardware Specialization:** Achieving optimal edge performance necessitates weight-activation quantization (e.g., INT4/INT8) and execution on dedicated hardware accelerators, such as the Hexagon NPU.[7] | **Mitigation:** Deployment should target models (e.g., Qwen2-VL 2B) and corresponding toolchains proven to integrate with NPU architecture, ensuring direct execution of quantized operations and eliminating dequantization overhead.[7] |
| **Runtime Complexity:** Optimized edge devices still face significant challenges in maximizing response quality, minimizing latency, and controlling usage costs across multi-modal tasks.[3] | **Mandates Hybrid Inference Offloading:** The implementation of the Three-M Offloading (TMO) framework is required, utilizing a lightweight local LLM for rapid processing of simple tasks and reserving a larger cloud LLM for complex, multi-modal reasoning.[3] | **Mitigation:** A rigorous Resource-Constrained Reinforcement Learning (RCRL) strategy must be developed to continuously optimize the local-vs-cloud offloading decision based on real-time resource constraints and predicted task complexity (multi-modal context, task type, dialogue history).[3] |

### 1.3. Recommended Deployment Strategy Blueprint: Local-Cloud Hybrid Architecture

The deployment strategy is structured into three interlocking phases designed to address the constraints inherent in mobile deployment:

**Phase I: Static Asset Optimization (Dynamic Delivery).** This phase centers on minimizing the initial installation footprint and ensuring fast access to essential MLLM components. Play Asset Delivery (PAD) must be used to architecturally fragment model weights based on the identified 80/20 parameter split, optimizing the initial installation flow and subsequent asset downloads.[5]

**Phase II: Edge Runtime Optimization.** This phase focuses on maximizing the efficiency of the local model execution. It is crucial to ensure that the selected compact model (e.g., Qwen2-VL 2B or Qwen2.5-VL-7B-Instruct) is compiled and deployed utilizing advanced weight-activation quantization. The runtime environment must be explicitly configured to execute these quantized operations directly on the dedicated hardware accelerator (NPU), such as the Hexagon NPU INT8 pipeline, for maximum speed and energy efficiency.[2]

**Phase III: Dynamic Runtime Management.** The final phase involves implementing the operational intelligence layer. This requires integrating the RCRL strategy atop the TMO framework. This sophisticated routing system will intelligently direct multi-modal, multi-task, and multi-dialogue requests between the rapid, low-cost local model and the powerful, high-quality cloud model, ensuring optimal resource allocation and user experience.[3]

---

## II. Architectural Foundations: Selecting and Structuring Compact MLLMs

### 2.1. Landscape Analysis of Mobile-Optimized VLMs (Sub-7B Category)

Successful edge deployment requires stringent model selection focused on models explicitly demonstrated as mobile-viable and supporting hardware-accelerated quantization profiles.[7] Several key models stand out in the compact MLLM category (under 7B parameters):

The **Qwen2-VL 2B** model is notable for its confirmed readiness for execution in highly constrained environments. It supports both CPU INT4 and dedicated Hexagon NPU INT8 quantization, making it an ideal candidate for achieving high throughput in environments where energy and computational budget are critical.[7]

Another critical candidate is the **Qwen2.5-VL-7B-Instruct**. While slightly larger, this model is specifically engineered and optimized for mobile deployment. It incorporates specialized training techniques, including dynamic resolution and frame rate training, essential for handling the highly variable, real-world visual inputs commonly captured and processed on mobile device sensors.[1] This specific optimization addresses a primary operational challenge of generic MLLMs on the edge: the potential degradation in visual comprehension accuracy when presented with non-standard mobile sensor data. By mitigating this accuracy degradation, this model directly contributes to a higher Response Quality (Q), which is a key component of the TMO system's optimization reward function.[3]

Other models, such as **LLaVA 7B** (which often uses a LLaMA-7B base) and **Phi-3-Vision**, employ modular structures, such as a simple linear projection layer, to bridge the pre-trained visual encoder (e.g., ViT-L/14 in CLIP) and the LLM.[8] **MiniCPM-V** also focuses on efficiency by adopting structures like the Q-Former to efficiently cut down the number of visual tokens processed for each image patch, reducing computational redundancy.[4]

### 2.2. Deconstructing the MLLM for Edge Deployment Payloads

For effective dynamic delivery, the complex structure of an MLLM must be deconstructed into its constituent parts based on parameter size and functionality. MLLMs typically comprise three main parts: the Visual Encoder, the Language Model (LLM), and a connecting Learnable Interface.[4]

Analysis of existing architectures, such as Qwen-VL, reveals a crucial parameter split that dictates the dynamic delivery strategy.[4] The largest component, the core LLM weights, accounts for the overwhelming majority of the overall size, often over **80.2%** (e.g., 7.7B parameters out of 9.7B total). The Visual Encoder (ViT/CLIP) constitutes the next largest segment, around **19.8%** (e.g., 1.9B parameters).[4] Finally, the Learnable Interface (such as the Q-Former or MLP connection layer) is comparatively negligible in size, often **less than 1%** (e.g., 0.08B), yet is essential for connecting the visual and linguistic components.[4]

This quantifiable parameter split provides the blueprint for strategic asset packaging. The immense size of the LLM weights makes this component the primary target for segmentation and dynamic delivery. Furthermore, the inherent structure of vision-language instruction tuning suggests a considerable redundancy in terms of computation and memory burden, which stems not only from the total weight size but from the inefficient processing of high-dimensional tensors.[8] This reinforces the necessity of adopting architectural techniques like the Q-Former, which reduces visual tokens processed [4], in conjunction with sophisticated weight compression (quantization) techniques to ensure holistic memory efficiency on the edge.[2] Deployment strategy must therefore address both the parameter count and the resulting memory access overheads.

**Table 1** summarizes the key mobile MLLM candidates based on their architectural optimization status.

#### Table 1: Mobile-Optimized Multi-Modal LLM Capabilities and Optimization Status

| Model | Parameters (Approx.) | Key Optimization | Quantization Support (Edge) | NPU Acceleration Ready | Primary Asset Payload % |
|-------|---------------------|------------------|----------------------------|----------------------|------------------------|
| **Qwen2-VL** | 2B | Maximum Efficiency, Small Footprint | CPU INT4 / NPU INT8 | Yes (Hexagon) [7] | High LLM/Encoder ratio [4] |
| **Qwen2.5-VL-7B-Instruct** | 7B | Dynamic Resolution/Frame Rate Training [1] | INT4 / INT8 (Inferred) | Yes (Inferred via Qwen family) | High LLM/Encoder ratio [4] |
| **LLaVA** | 7B+ (LLaMA-7B base) | Linear Projection Bridge [8] | Varies (e.g., INT4) | Dependent on Base LLM | High LLM/Encoder ratio [8] |
| **MiniCPM-V** | Sub-7B | Q-Former for Visual Token Reduction [4] | Highly Efficient | Targeted for End-Side Computation [4] | Efficient management of memory burden |

---

## III. Deep Dive into Edge Optimization: Quantization and NPU Acceleration

### 3.1. Advanced Weight and Activation Quantization Techniques

Model optimization requires moving beyond basic compression techniques. While weight-only quantization (such as AWQ) successfully reduces the model size [6], the resultant gains in inference latency are often marginal, largely confined to reductions in memory access overheads.[2] This limitation arises because computation is still performed in floating-point, necessitating costly on-the-fly weight dequantization during inference, which remains energy-intensive and time-consuming on edge devices.[2]

For truly significant gains in both latency reduction and energy efficiency, the strategic focus must shift to **weight-activation quantization**.[2] By quantizing both weights and activations to INT4 or INT8, the system can forgo the high computational and energy cost of dequantization, allowing computation to be performed entirely in the lower-bit format. Given that high latency and high energy consumption are major inhibitors of widespread edge deployment [2], establishing a favorable energy-per-inference profile is directly linked to the operational success of the hybrid system. Specifically, improving energy efficiency through advanced quantization directly enhances the Cost (C) metric tracked by the Resource-Constrained Reinforcement Learning (RCRL) offloading algorithm, enabling a greater volume of tasks to be executed locally.[3]

### 3.2. Hardware Integration and Inference Scheduling

The viability of executing complex multi-modal models locally is predicated on the availability and utilization of specialized hardware. Compact MLLMs, exemplified by Qwen2-VL 2B, demonstrate the potential for direct execution of INT8 quantized models on dedicated Neural Processing Units (NPUs), specifically citing Hexagon NPU support.[7]

NPUs are essential as they are purpose-built to handle the massive, parallel matrix multiplication operations that dominate transformer architecture computations. Utilizing the NPU for weight-activation quantized execution ensures high throughput and minimal energy consumption, addressing the high computational load introduced by processing visual data.[2] Furthermore, architectural efficiencies, such as those implemented by MobileLLM (which focuses on architectural optimization and weight sharing) [6], complement aggressive quantization by reducing the theoretical computation volume needed, ensuring maximum resource utilization alongside NPU acceleration.

### 3.3. Trade-off Modeling: Latency vs. Accuracy vs. Model Size

Mobile deployment fundamentally requires navigating a non-negotiable trade-off between model size (storage and delivery efficiency), inference latency (user response time), and response quality/accuracy (functional integrity).

To manage this trilemma effectively, the deployment strategy must adopt advanced architectural patterns. Specifically, incorporating **Hot/Cold Memory Tiers** allows the system to prioritize latency and cost for the majority of user interactions.[9] It is generally observed that 70% to 80% of routine queries can be answered quickly and efficiently from a "hot tier" of context and model layers resident on the device.[9] Only when complex, less frequent multi-modal tasks arise—requiring deeper context or high-accuracy reasoning—should the system incur the costs of retrieving data from the "cold tier" or offloading to the cloud.[3]

This Multimodal Memory tracking system, which must handle context across text conversations, code, image uploads, and voice interactions [9], becomes the crucial initial factor for the offloading decision. The state of this memory system—whether the context necessary for a high-quality answer resides locally (hot tier) or remotely (cold tier)—serves as a primary input for the RCRL decision agent.[3] If the RCRL algorithm determines that the local hot tier memory is sufficient to generate a high-quality response, the system is engineered to prioritize local execution, maximizing speed and minimizing cost, regardless of the task's perceived general complexity.

---

## IV. Strategic Deployment: Implementation of Dynamic Model Delivery

### 4.1. The Mechanism of Play Asset Delivery (PAD) for AI Assets

Dynamic delivery is the key logistical component for successful MLLM deployment, enabling the management of large assets (model weights) without compromising the initial application install size. **Play Asset Delivery (PAD)** fulfills this requirement by hosting and serving large model weights as asset packs on Google Play, eliminating the necessity for developing and maintaining custom Content Delivery Network (CDN) infrastructure.[5] The critical requirement is that these assets must be model weights, textures, or sounds, without executable code.[5]

PAD offers three distinct delivery modes that serve as strategic levers for managing the deployment flow:

- **Install-Time:** Assets downloaded immediately with the application package, reserved for minimal, essential components.
- **Fast-Follow:** Assets downloaded automatically immediately after the application installation is complete, ideal for high-priority components required for the first interaction.
- **On-Demand:** Assets downloaded only when explicitly requested at runtime by the application, suitable for the largest, least frequently utilized components.[5]

### 4.2. Mapping MLLM Components to Dynamic Delivery Modes

The MLLM component split established in Section II must be rigorously mapped to the PAD delivery modes. The objective is to ensure minimal initial application download size while avoiding feature gating—the delay experienced by the user when a required component is not yet available.

#### Table 2: Mapping MLLM Assets to Dynamic Delivery Modes

| MLLM Component Asset | Approximate Size Share | Suggested PAD Delivery Mode | Rationale for Selection | Inference Use Case Trigger |
|---------------------|----------------------|---------------------------|------------------------|---------------------------|
| **Learnable Interface (Q-Former/MLP)** | <1% (0.08B) [4] | Install-Time | Minimal payload impact; critical for immediately loading core model functionality upon launch. | Application Initialization / Core ML Load |
| **Visual Encoder (ViT/CLIP Weights)** | ~20% (1.9B) [4] | Fast-Follow | Essential for all multi-modal capabilities; downloading post-install ensures a responsive first interaction when visual input is detected. | Completion of app installation. |
| **Core Language Model (LLM Weights - Base Layer)** | ~40% (Segment 1) | Fast-Follow | Required for fast, simple local processing (the 70-80% "hot tier" queries).[9] Must be available quickly to support RCRL local execution.[3] | Completion of app installation. |
| **Core Language Model (LLM Weights - Complex Layers)** | ~40% (Segment 2) | On-Demand | Largest payload; reserved for complex, multi-turn dialogue, or advanced reasoning that may be offloaded to the cloud anyway. | Activation of high-complexity features or explicit user consent for advanced capabilities. |
| **Quantization Metadata/Configuration** | Negligible | Install-Time | Required immediately by the local runtime engine to correctly interpret and load the INT4/INT8 weights.[2] | Application initialization and model loading. |

The strategy emphasizes segmenting the dominant LLM payload (80% of parameters).[4] By delivering the first ~40% segment via Fast-Follow, the system ensures that a functional, lightweight local LLM is quickly available to handle the majority of simple tasks, thereby maximizing the effective utilization of local inference capabilities before the user encounters the full latency required to download the most complex layers. This fragmentation is crucial for maintaining perceived performance and maximizing the success rate of local inference attempts.

A secondary consideration for dynamic delivery is maintaining data integrity. Fragmenting model weights across disparate delivery modes introduces the risk of version skew. For instance, if a user updates the Visual Encoder (Fast-Follow) but the On-Demand LLM segment remains an outdated version, the model connection may fail. The deployment pipeline must strictly enforce **atomic versioning** across all related asset packs (Encoder, all LLM Segments, and Interface) to guarantee functional compatibility upon load.

---

## V. Hybrid Inference Systems: Optimizing Dynamic Runtime Performance

### 5.1. The Necessity of Local-Cloud Offloading in Multi-Modal Systems

Even with aggressive optimization via quantization and NPU acceleration, constraints in memory, computation, and energy capacity often prohibit executing every request locally.[2] This reality necessitates an intelligent operational framework to manage computational demands.

The **Three-M Offloading (TMO) framework** is the necessary architectural solution for this challenge.[3] TMO integrates a lightweight, compact local LLM (for high-speed, simple task execution) with a large-scale cloud LLM (reserved for handling complex, multi-modal data sources and advanced reasoning capabilities). This structure allows the system to balance local device resource limitations against the higher quality and deeper context provided by a powerful cloud model.[3] Crucially, TMO must also incorporate mechanisms to quantify and manage the inherent communication latency and monetary usage costs associated with routing requests to the cloud.

### 5.2. The Three-M Offloading (TMO) Framework and Decision Strategy

The TMO system derives its complexity from managing three distinct dimensions of interaction [3]:

1. **Multi-Modal Dimension:** The decision must account for mixed input types (text, image, audio, context). Visual processing introduces high computational load, and the decision must weigh the speed of the local visual encoder processing against the latency of transmitting large visual data to the cloud for processing.

2. **Multi-Task Dimension:** The system evaluates the nature of the task. Simple summarization might be handled by the lightweight local model, whereas complex reasoning, synthesis, or structured output generation might require the advanced capabilities residing only in the large cloud model.[3]

3. **Multi-Dialogue Dimension:** The decision relies heavily on the status of the Multimodal Memory system.[9] If the context required for a high-quality answer is readily available in the local "Hot Tier" (e.g., recent interactions), local execution is highly favored. If the required context has expired locally or resides in the "Cold Tier" (long-term, complex history), offloading to the cloud is often warranted to achieve maximum response quality.[9]

### 5.3. Implementing Resource-Constrained Reinforcement Learning (RCRL) for Optimal Routing

The intelligent core of the TMO framework is the **Resource-Constrained Reinforcement Learning (RCRL)** strategy. RCRL acts as an adaptive agent, continuously optimizing the choice of inference location (local vs. cloud) for each task. Its goal is to maximize the long-term cumulative reward while ensuring adherence to immediate local resource constraints (CPU load, memory availability, energy status).[3]

The RCRL agent's reward function explicitly weighs three critical metrics in real-time:

- **Response Quality (Q):** Accuracy, depth, and detail of the output (typically favoring the cloud).
- **Latency (L):** Time taken for the response (heavily favoring the local NPU execution).
- **Usage Cost (C):** Energy consumed locally and data transfer/monetary cost incurred in the cloud (heavily favoring local execution).[3]

Effective RCRL implementation requires a comprehensive and operationally relevant dataset that maps these reward and cost metrics across diverse configurations—modality, task type, dialogue complexity, and LLM architecture. Research indicates the importance of tailored data sets, such as the curated M4A1 dataset, for evaluating offloading decisions.[3] This implies that ongoing project resources must be allocated not only to developing the RCRL model but also to meticulously collecting and refining domain-specific metrics that reflect real-world usage patterns. This ensures the RCRL agent is continuously learning and accurately predicting the true long-term cost and benefit of local versus cloud inference, guaranteeing adaptive and efficient operation.

---

## VI. Project Recommendations and Implementation Road Map

### 6.1. Priority Action Items for MLLM Asset Delivery Integration

Based on the strategic analysis, the following actions are necessary to update the project timeline:

**Action 1: Model Selection & NPU Toolchain Validation (Q1).** Finalize the choice between leading candidates, such as Qwen2.5-VL-7B-Instruct (due to its specialized dynamic training [1]) and other compact models, based on rigorous validation of weight-activation quantization efficacy (e.g., INT8) and proven integration with the target mobile NPU hardware (e.g., Hexagon support).[2]

**Action 2: Define Asset Segmentation Plan (Q1/Q2).** Formally define the architectural fragmentation of the selected MLLM based on the 80/20 parameter split.[4] The exact segment boundary between the Fast-Follow component (essential local MLLM layers) and the On-Demand component (complex LLM layers) must be established using the framework outlined in Table 2.

**Action 3: PAD Implementation & Testing (Q2).** Integrate the selected asset packs into the Play Asset Delivery pipeline. Focused testing must be prioritized on verifying atomic version control across all fragmented asset packs to eliminate the critical risk of version skew failure modes during updates.

### 6.2. Risk Mitigation Strategies for Quantization and Hybrid Inference

**Quantization Risk (Accuracy Degradation):** The primary risk is that aggressive quantization to INT4 or INT8 will cause unacceptable degradation in response quality, particularly for complex multi-modal tasks.

**Mitigation:** Implement rigorous, automated A/B testing comparing the FP32 cloud responses against the quantized local responses. The resulting accuracy degradation must be treated as a hard input threshold for RCRL routing decisions. If local response quality falls below a defined operational threshold, the RCRL agent must temporarily force requests to the cloud, allowing time for model finetuning to restore local performance.

**Offloading Risk (RCRL Instability):** The RCRL agent could potentially fail, leading to "thrashing"—inefficient, rapid switching between local and cloud execution, incurring high communication costs and unpredictable latency.

**Mitigation:** The RCRL reward function must incorporate penalty metrics for frequent, consecutive offloading switches (hysteresis). The RCRL model must be continuously retrained using operational M4A1-style metrics to ensure its decision-making remains adaptive and stable against shifting network conditions and resource constraints.[3]

### 6.3. Future Architectural Evolution

The current project must establish the groundwork for future scalability and improved context management:

**Short-Term Goal (Q3):** Fully implement the Multimodal Memory tracking system, which tracks conversation, image, and other relevant context.[9] The status of this memory (hot vs. cold tier context availability) must be engineered as a mandatory feature input for the RCRL engine to optimize decision-making based on required context depth.

**Long-Term Goal (Q4+):** Investigate advanced architectural mechanisms focused on efficiency, such as further visual token reduction techniques utilized by models like MiniCPM-V [4], and explore inference offloading strategies designed specifically for multi-dialogue settings to ensure long-term scalability and cost control.[3]

---

## References

[1] Qwen2.5-VL: Dynamic Resolution & Frame Rate Training for Mobile Vision-Language Models
[2] Edge AI Optimization: Weight-Activation Quantization for Efficient Inference
[3] Three-M Offloading (TMO): Multi-Modal, Multi-Task, Multi-Dialogue Framework with RCRL
[4] MLLM Architecture Analysis: Parameter Distribution in Vision-Language Models
[5] Play Asset Delivery (PAD): Dynamic Model Distribution on Android
[6] Weight-Only Quantization Limitations in Mobile Inference
[7] Qwen2-VL 2B: NPU-Optimized INT8 Quantization on Hexagon
[8] LLaVA Architecture: Linear Projection for Vision-Language Integration
[9] Hot/Cold Memory Tiers: Context Management for Efficient Edge Inference

---

**Document Status:** Executive Strategy Report
**Last Updated:** 2025-11-01
**Target Audience:** Technical Leadership, Product Management, ML Engineering
**Classification:** Strategic Planning - Internal Use