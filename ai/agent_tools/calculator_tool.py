import ast
import math
import operator
from typing import Any, Callable

from langchain_core.tools import tool


_BINARY_OPERATORS: dict[type[ast.operator], Callable[[Any, Any], Any]] = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.FloorDiv: operator.floordiv,
    ast.Mod: operator.mod,
    ast.Pow: operator.pow,
}

_UNARY_OPERATORS: dict[type[ast.unaryop], Callable[[Any], Any]] = {
    ast.UAdd: operator.pos,
    ast.USub: operator.neg,
}

_FUNCTIONS: dict[str, Callable[..., Any]] = {
    "abs": abs,
    "round": round,
    "sqrt": math.sqrt,
    "pow": pow,
    "sin": math.sin,
    "cos": math.cos,
    "tan": math.tan,
    "log": math.log,
    "log10": math.log10,
}

_CONSTANTS = {
    "pi": math.pi,
    "e": math.e,
}


def _normalize_expression(expression: str) -> str:
    return (
        expression.strip()
        .replace("×", "*")
        .replace("÷", "/")
        .replace("^", "**")
        .replace("（", "(")
        .replace("）", ")")
    )


def _evaluate_node(node: ast.AST) -> Any:
    if isinstance(node, ast.Expression):
        return _evaluate_node(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return node.value
    if isinstance(node, ast.BinOp) and type(node.op) in _BINARY_OPERATORS:
        return _BINARY_OPERATORS[type(node.op)](_evaluate_node(node.left), _evaluate_node(node.right))
    if isinstance(node, ast.UnaryOp) and type(node.op) in _UNARY_OPERATORS:
        return _UNARY_OPERATORS[type(node.op)](_evaluate_node(node.operand))
    if isinstance(node, ast.Name) and node.id in _CONSTANTS:
        return _CONSTANTS[node.id]
    if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
        func = _FUNCTIONS.get(node.func.id)
        if func is None or node.keywords:
            raise ValueError("不支持的函数调用")
        return func(*[_evaluate_node(arg) for arg in node.args])
    raise ValueError("表达式只能包含数字、基础运算符和允许的数学函数")


def _format_number(value: Any) -> str:
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


@tool
def math_calculate(expression: str) -> str:
    """
    计算数学表达式。适用于算术、平方根、幂、三角函数、对数等数值计算。
    输入必须是表达式，例如 sqrt(25) + 36、(2 + 3) * 4。
    """
    try:
        normalized = _normalize_expression(expression)
        if not normalized or len(normalized) > 200:
            raise ValueError("表达式为空或过长")
        tree = ast.parse(normalized, mode="eval")
        result = _evaluate_node(tree)
        return f"计算表达式：{normalized}\n计算结果：{_format_number(result)}"
    except Exception as exc:
        return f"计算失败：{exc}"
