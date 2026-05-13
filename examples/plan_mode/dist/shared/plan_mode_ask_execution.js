"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.submitPlanaskAnswers = submitPlanaskAnswers;
const plan_mode_i18n_js_1 = require("./plan_mode_i18n.js");
const plan_mode_ask_js_1 = require("./plan_mode_ask.js");
const plan_mode_state_js_1 = require("./plan_mode_state.js");
function toErrorText(error) {
    if (error instanceof Error) {
        return error.message || "error";
    }
    return error || "error";
}
async function submitPlanaskAnswers(ctx, parsed, answerTexts) {
    const text = (0, plan_mode_i18n_js_1.resolvePlanModeI18n)();
    try {
        const activeView = (0, plan_mode_state_js_1.readSingleActiveChatView)();
        if (!activeView) {
            await ctx.showToast(text.toastChatViewMissing);
            return {
                success: false,
                error: text.toastChatViewMissing,
            };
        }
        const message = (0, plan_mode_ask_js_1.buildPlanaskAnswerMessage)(parsed, answerTexts);
        void Tools.Chat.sendMessage(message, activeView.chatId, undefined, undefined, { runtime: activeView.runtime }).catch(async (error) => {
            const errorText = error instanceof Error || typeof error === "string" || error == null
                ? toErrorText(error)
                : "error";
            await ctx.showToast(`${text.askToastAnswerSendFailedPrefix}${errorText}`);
        });
        await ctx.showToast(text.askToastAnswerSent);
        return {
            success: true,
            message,
        };
    }
    catch (error) {
        const errorText = error instanceof Error || typeof error === "string" || error == null
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
