#ifndef TOKEN_MAPPER_H
#define TOKEN_MAPPER_H

#include <string>
#include <unordered_map>
#include <vector>

class TokenMapper {
public:
    TokenMapper();
    ~TokenMapper();

    // 初始化硬编码的token映射
    void initialize();
    
    // 根据ID获取token
    std::string getTokenById(int id) const;
    
    // 根据token获取ID
    int getIdByToken(const std::string& token) const;
    
    // 检查是否已初始化
    bool isInitialized() const { return initialized_; }
    
    // 获取token总数
    size_t size() const { return id_to_token_.size(); }

private:
    // ID到token的映射
    std::unordered_map<int, std::string> id_to_token_;
    
    // token到ID的映射
    std::unordered_map<std::string, int> token_to_id_;
    
    // 初始化标志
    bool initialized_ = false;
};

#endif // TOKEN_MAPPER_H