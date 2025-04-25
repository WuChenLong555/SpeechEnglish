import torch
import torchaudio
from transformers import Wav2Vec2Processor, Wav2Vec2ForCTC
import torch.nn as nn

class Wav2Vec2Emissions(nn.Module):
    def __init__(self, wav2vec2):
        super().__init__()
        self.wav2vec2 = wav2vec2
        
    def forward(self, input_values):
        # 只返回logits/emissions
        outputs = self.wav2vec2(input_values)
        # 添加log_softmax层，这样在C++端就不需要额外计算
        log_probs = torch.nn.functional.log_softmax(outputs.logits, dim=-1)
        return log_probs

def wav2vec2_to_pnnx(model_name="facebook/wav2vec2-lv-60-espeak-cv-ft", 
                     output_path="wav2vec2_emissions",
                     audio_path=r"D:\study\研究生\研究方向\新的文件\数据集\filtered_prosodylab_corpus 2.0\speakers_test\speaker0001\000010069.wav",
                     sample_rate=16000):
    """
    将Wav2Vec2模型转换为PNNX格式
    
    Args:
        model_name: HuggingFace模型名称
        output_path: 输出模型路径（不需要扩展名）
        audio_path: 测试音频文件路径
        sample_rate: 目标采样率
    """
    try:
        # 1. 加载原始模型
        print("加载原始模型...")
        model = Wav2Vec2ForCTC.from_pretrained(model_name)
        model.eval()
        
        # 2. 创建emissions模型
        print("创建emissions模型...")
        emissions_model = Wav2Vec2Emissions(model)
        emissions_model.eval()
        
        # 3. 加载并处理测试音频
        print("加载测试音频...")
        waveform, sr = torchaudio.load(audio_path)
        if sr != sample_rate:
            waveform = torchaudio.transforms.Resample(orig_freq=sr, new_freq=sample_rate)(waveform)
        
        # 确保输入形状正确 [batch_size, sequence_length]
        test_audio = waveform.unsqueeze(0) if waveform.dim() == 1 else waveform
        
        # 4. 转换为TorchScript
        print("转换为TorchScript格式...")
        with torch.no_grad():
            traced_script_module = torch.jit.trace(emissions_model, test_audio)
        
        # 5. 验证转换结果
        print("验证转换结果...")
        with torch.no_grad():
            output1 = traced_script_module(test_audio)
            output2 = emissions_model(test_audio)
        
        if not torch.allclose(output1, output2, rtol=1e-03, atol=1e-05):
            raise ValueError("转换后的模型输出与原始模型不匹配！")
        
        # 6. 保存TorchScript模型
        torchscript_path = f"{output_path}.pt"
        print(f"保存TorchScript模型到 {torchscript_path}...")
        traced_script_module.save(torchscript_path)
        
        # 7. 输出模型信息
        print(f"\n模型信息:")
        print(f"输入音频: {audio_path}")
        print(f"输入形状: {test_audio.shape}")
        print(f"输出形状: {output1.shape}")
        
        print("\n转换完成！")
        print("请使用pnnx工具将TorchScript模型转换为PNNX格式：")
        print(f"pnnx {torchscript_path}")
        
        return True
        
    except Exception as e:
        print(f"转换失败: {str(e)}")
        raise

def verify_torchscript_model(model_path, 
                            audio_path=r"D:\study\研究生\研究方向\新的文件\数据集\filtered_prosodylab_corpus 2.0\speakers_test\speaker0001\000010069.wav",
                            sample_rate=16000):
    """验证转换后的TorchScript模型"""
    try:
        # 1. 加载转换后的模型
        print("加载模型进行验证...")
        loaded_model = torch.jit.load(model_path)
        
        # 2. 加载测试音频
        print("加载测试音频...")
        waveform, sr = torchaudio.load(audio_path)
        if sr != sample_rate:
            waveform = torchaudio.transforms.Resample(orig_freq=sr, new_freq=sample_rate)(waveform)
        
        # 确保输入形状正确
        test_audio = waveform.unsqueeze(0) if waveform.dim() == 1 else waveform
        
        # 3. 运行推理
        print("执行推理测试...")
        with torch.no_grad():
            output = loaded_model(test_audio)
        
        print(f"推理成功！输出形状: {output.shape}")
        
        # 4. 检查输出的基本属性
        print("\n输出检查:")
        print(f"- 输入音频: {audio_path}")
        print(f"- 输入形状: {test_audio.shape}")
        print(f"- 输出形状: {output.shape}")
        print(f"- 数值范围: [{output.min():.3f}, {output.max():.3f}]")
        print(f"- 是否包含NaN: {torch.isnan(output).any()}")
        print(f"- 是否包含Inf: {torch.isinf(output).any()}")
        
        return True
        
    except Exception as e:
        print(f"验证失败: {str(e)}")
        raise

if __name__ == "__main__":
    # 设置参数
    MODEL_NAME = "facebook/wav2vec2-lv-60-espeak-cv-ft"
    OUTPUT_PATH = "wav2vec2_emissions"
    AUDIO_PATH = r"D:\study\研究生\研究方向\新的文件\数据集\filtered_prosodylab_corpus 2.0\speakers_test\speaker0001\000010069.wav"
    SAMPLE_RATE = 16000
    
    # 执行转换
    print("开始模型转换...")
    wav2vec2_to_pnnx(
        model_name=MODEL_NAME,
        output_path=OUTPUT_PATH,
        audio_path=AUDIO_PATH,
        sample_rate=SAMPLE_RATE
    )
    
    # 验证转换后的模型
    print("\n开始模型验证...")
    verify_torchscript_model(
        model_path=f"{OUTPUT_PATH}.pt",
        audio_path=AUDIO_PATH,
        sample_rate=SAMPLE_RATE
    ) 