#!/bin/bash
# Fix deprecated Clock.System.now().toEpochMilliseconds() -> .epochMilliseconds

set -e

FILES=(
    "shared/src/commonMain/kotlin/app/m1k3/ai/domain/usecases/chat/ChatWithToolsUseCase.kt"
    "shared/src/commonMain/kotlin/app/m1k3/ai/domain/usecases/chat/SendMessageUseCase.kt"
    "shared/src/commonMain/kotlin/app/m1k3/ai/domain/usecases/memory/CreateMemoryUseCase.kt"
    "shared/src/commonMain/kotlin/app/m1k3/ai/domain/usecases/tools/ExecuteToolUseCase.kt"
    "shared/src/commonTest/kotlin/app/m1k3/ai/domain/memory/MemoryManagerInterfaceTest.kt"
    "shared/src/commonTest/kotlin/app/m1k3/ai/domain/DomainLayerIntegrationTest.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/avatar/AvatarEngine.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/memory/MemoryManager.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/history/ExportManager.kt"
    "composeApp/src/commonTest/kotlin/app/m1k3/ai/assistant/history/SearchRepositoryTest.kt"
    "composeApp/src/commonTest/kotlin/app/m1k3/ai/assistant/eco/EcoMetricsRepositoryTest.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/chat/usecase/ChatWithToolsUseCase.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/memory/MemoryRanker.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/chat/usecase/SendMessageUseCase.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/history/ConversationRepository.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/avatar/PetMetricsRepository.kt"
    "composeApp/src/commonTest/kotlin/app/m1k3/ai/assistant/history/ExportManagerTest.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/chat/ChatScreenViewModel.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/design/preview/PreviewFixtures.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/avatar/PetViewModel.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/demo/DatabaseDemo.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/eco/EcoMetricsRepository.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/design/components/MaChatBubble.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/avatar/AvatarView.kt"
    "composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/knowledge/KnowledgeBaseImporter.kt"
)

COUNT=0
for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        # Use sed to replace .toEpochMilliseconds() with .epochMilliseconds
        # Works on both macOS and Linux
        if sed -i.bak 's/\.toEpochMilliseconds()/.epochMilliseconds/g' "$file"; then
            rm -f "${file}.bak"
            echo "✓ Fixed: $file"
            ((COUNT++))
        fi
    else
        echo "⚠ Skipped (not found): $file"
    fi
done

echo ""
echo "✓ Fixed $COUNT files"
echo "✓ Replaced .toEpochMilliseconds() → .epochMilliseconds"
