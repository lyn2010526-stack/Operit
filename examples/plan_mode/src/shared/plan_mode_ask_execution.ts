import type { ComposeDslContext } from "../../../types/compose-dsl";
import { resolvePlanModeI18n } from "./plan_mode_i18n.js";
import { buildPlanaskAnswerMessage, type ParsedPlanask } from "./plan_mode_ask.js";
import { readSingleActiveChatView } from "./plan_mode_state.js";

export type SubmitPlanaskAnswersResult = {
  success: boolean;
  error?: string;
  message?: string;
};

function toErrorText(error: Error | string | null | undefined): string {
  if (error instanceof Error) {
    return error.message || "error";
  }
  return error || "error";
}

export async function submitPlanaskAnswers(
  ctx: ComposeDslContext,
  parsed: ParsedPlanask,
  answerTexts: Record<string, string>
): Promise<SubmitPlanaskAnswersResult> {
  const text = resolvePlanModeI18n();
  try {
    const activeView = readSingleActiveChatView();
    if (!activeView) {
      await ctx.showToast(text.toastChatViewMissing);
      return {
        success: false,
        error: text.toastChatViewMissing,
      };
    }

    const message = buildPlanaskAnswerMessage(parsed, answerTexts);
    void Tools.Chat.sendMessage(
      message,
      activeView.chatId,
      undefined,
      undefined,
      { runtime: activeView.runtime }
    ).catch(async (error) => {
      const errorText =
        error instanceof Error || typeof error === "string" || error == null
          ? toErrorText(error)
          : "error";
      await ctx.showToast(`${text.askToastAnswerSendFailedPrefix}${errorText}`);
    });
    await ctx.showToast(text.askToastAnswerSent);
    return {
      success: true,
      message,
    };
  } catch (error) {
    const errorText =
      error instanceof Error || typeof error === "string" || error == null
        ? toErrorText(error)
        : "error";
    const message = `${text.askToastAnswerSendFailedPrefix}${errorText}`;
    await ctx.showToast(message);
    return {
      success: false,
      error: message,
    };
  }
}
