import base64
import json
import os
import re
import sys
import urllib.request
from io import BytesIO
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    Image,
    KeepTogether,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(r"D:\Mooic")
DATA_PATH = ROOT / "tmp" / "pdfs" / "information_theory_questions.json"
OUT_PATH = ROOT / "output" / "pdf" / "信息论基础六章作业题干与正确答案.pdf"
IMG_DIR = ROOT / "tmp" / "pdfs" / "images"


def register_font() -> str:
    candidates = [
        r"C:\Windows\Fonts\simhei.ttf",
        r"C:\Windows\Fonts\simsun.ttc",
        r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\NotoSansSC-VF.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                pdfmetrics.registerFont(TTFont("CJK", path))
                return "CJK"
            except Exception:
                continue
    raise RuntimeError("No usable Chinese font found.")


FONT = register_font()


def safe_name(url: str, index: int) -> str:
    suffix = Path(url.split("?", 1)[0]).suffix.lower()
    if suffix not in {".png", ".jpg", ".jpeg", ".bmp", ".gif", ".webp"}:
        suffix = ".img"
    key = re.sub(r"[^a-zA-Z0-9]+", "_", url)[-80:]
    return f"{index:03d}_{key}{suffix}"


def download_image(url: str, index: int) -> Path:
    IMG_DIR.mkdir(parents=True, exist_ok=True)
    target = IMG_DIR / safe_name(url, index)
    if target.exists() and target.stat().st_size > 0:
        return target
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://mooc1.chaoxing.com/",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = resp.read()
    if not data:
        raise RuntimeError(f"Empty image download: {url}")
    target.write_bytes(data)
    return target


def image_from_data_url(data_url: str, index: int) -> Path:
    IMG_DIR.mkdir(parents=True, exist_ok=True)
    header, payload = data_url.split(",", 1)
    ext = ".png"
    if "jpeg" in header or "jpg" in header:
        ext = ".jpg"
    elif "bmp" in header:
        ext = ".bmp"
    target = IMG_DIR / f"embedded_{index:03d}{ext}"
    target.write_bytes(base64.b64decode(payload))
    return target


def clean(text: str) -> str:
    return re.sub(r"\n{3,}", "\n\n", str(text or "").replace("\u00a0", " ")).strip()


def para(text: str, style: ParagraphStyle) -> Paragraph:
    escaped = (
        clean(text)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br/>")
    )
    return Paragraph(escaped or " ", style)


def fit_image(path: Path, max_width: float) -> Image:
    img = Image(str(path))
    if img.drawWidth > max_width:
        ratio = max_width / img.drawWidth
        img.drawWidth *= ratio
        img.drawHeight *= ratio
    return img


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont(FONT, 8)
    canvas.setFillColor(colors.HexColor("#666666"))
    canvas.drawCentredString(A4[0] / 2, 10 * mm, f"第 {doc.page} 页")
    canvas.restoreState()


def build_pdf():
    data = json.loads(DATA_PATH.read_text(encoding="utf-8"))
    chapters = data["chapters"]
    total_questions = sum(len(c["questions"]) for c in chapters)
    if len(chapters) != 6 or total_questions != 47:
        raise RuntimeError(f"Unexpected extraction size: {len(chapters)} chapters, {total_questions} questions")

    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        "TitleCJK",
        parent=styles["Title"],
        fontName=FONT,
        fontSize=20,
        leading=28,
        alignment=TA_CENTER,
        spaceAfter=8 * mm,
    )
    h1 = ParagraphStyle(
        "Chapter",
        parent=styles["Heading1"],
        fontName=FONT,
        fontSize=15,
        leading=20,
        textColor=colors.HexColor("#1F2937"),
        spaceBefore=4 * mm,
        spaceAfter=3 * mm,
    )
    qhead = ParagraphStyle(
        "QuestionHead",
        parent=styles["Heading2"],
        fontName=FONT,
        fontSize=11.5,
        leading=16,
        textColor=colors.HexColor("#111827"),
        spaceAfter=2 * mm,
    )
    body = ParagraphStyle(
        "BodyCJK",
        parent=styles["BodyText"],
        fontName=FONT,
        fontSize=9.6,
        leading=15,
        spaceAfter=1.8 * mm,
    )
    answer = ParagraphStyle(
        "Answer",
        parent=body,
        textColor=colors.HexColor("#047857"),
        fontName=FONT,
    )
    small = ParagraphStyle(
        "Small",
        parent=body,
        fontName=FONT,
        fontSize=8.2,
        leading=12,
        textColor=colors.HexColor("#6B7280"),
    )

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(
        str(OUT_PATH),
        pagesize=A4,
        rightMargin=16 * mm,
        leftMargin=16 * mm,
        topMargin=15 * mm,
        bottomMargin=16 * mm,
        title="信息论基础六章作业题干与正确答案",
    )
    story = [
        para("信息论基础六章作业题干与正确答案", title),
        para("范围：仅包含选择题与填空题；答案逐题来自作业详情页的“正确答案”字段。", small),
        para(f"共 6 章，{total_questions} 道题。生成日期：2026-07-04。", small),
        Spacer(1, 4 * mm),
    ]

    image_index = 0
    downloaded = 0
    for c_index, chapter in enumerate(chapters):
        if c_index:
            story.append(PageBreak())
        story.append(para(chapter["chapter"], h1))
        for q in chapter["questions"]:
            if not q.get("stem") or not q.get("answer"):
                raise RuntimeError(f"Missing stem/answer: {chapter['chapter']} question {q.get('num')}")
            block = [
                para(f"{q['num']}. {q['type']}", qhead),
                para(f"题干：{q['stem']}", body),
            ]
            options = [opt for opt in q.get("options", []) if opt.strip()]
            if options:
                block.append(para("选项：", body))
                for opt in options:
                    block.append(para(opt, body))
            images = q.get("images") or []
            if images:
                block.append(para("题目图片/公式：", small))
                for img in images:
                    image_index += 1
                    if img.get("dataUrl"):
                        img_path = image_from_data_url(img["dataUrl"], image_index)
                    else:
                        img_path = download_image(img["src"], image_index)
                    downloaded += 1
                    block.append(fit_image(img_path, doc.width - 10 * mm))
                    block.append(Spacer(1, 1.5 * mm))
            block.append(para(f"正确答案：{q['answer']}", answer))
            table = Table([[block]], colWidths=[doc.width])
            table.setStyle(
                TableStyle(
                    [
                        ("BOX", (0, 0), (-1, -1), 0.4, colors.HexColor("#D1D5DB")),
                        ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#FAFAFA")),
                        ("LEFTPADDING", (0, 0), (-1, -1), 6),
                        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                        ("TOPPADDING", (0, 0), (-1, -1), 6),
                        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
                    ]
                )
            )
            story.append(KeepTogether([table, Spacer(1, 3 * mm)]))

    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    return {"pdf": str(OUT_PATH), "questions": total_questions, "images": downloaded}


if __name__ == "__main__":
    result = build_pdf()
    print(json.dumps(result, ensure_ascii=False, indent=2))
