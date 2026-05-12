"""
【作用】最小化验证本机能否从 HuggingFace 或缓存拉取 bert-base-chinese 并完成一次加载。
【效果】控制台打印词表大小与隐藏维度；不训练、不写文件，仅作环境与网络检查。

首次运行会从 Hugging Face 下载 bert-base-chinese 到本机缓存（默认在用户目录 .cache/huggingface）。
国内网络慢可先在系统/运行配置里设置环境变量：HF_ENDPOINT=https://hf-mirror.com
"""
from transformers import BertModel, BertTokenizer

MODEL_NAME = "bert-base-chinese"


def main() -> None:
    print(f"加载模型：{MODEL_NAME}（首次为下载，之后读缓存）…")
    tokenizer = BertTokenizer.from_pretrained(MODEL_NAME)
    model = BertModel.from_pretrained(MODEL_NAME)
    print("完成。")
    print(f"Tokenizer 词表大小: {tokenizer.vocab_size}")
    print(f"模型隐藏维度: {model.config.hidden_size}")


if __name__ == "__main__":
    main()
