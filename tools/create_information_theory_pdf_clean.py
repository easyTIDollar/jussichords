import base64
import json
import os
import re
import urllib.request
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
            pdfmetrics.registerFont(TTFont("CJK", path))
            return "CJK"
    raise RuntimeError("No usable Chinese font found.")


FONT = register_font()


def clean(text: str) -> str:
    text = str(text or "").replace("\u00a0", " ")
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def display_stem(question: dict) -> str:
    stem = clean(question.get("stem", ""))
    stem = re.sub(r"\[图片:https?://[^\]]+\]", "", stem).strip()
    stem = re.sub(r"[ \t]{2,}", " ", stem)
    if not stem and question.get("images"):
        return "见下方题目图片/公式。"
    return stem


def paragraph(text: str, style: ParagraphStyle) -> Paragraph:
    escaped = (
        clean(text)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br/>")
    )
    return Paragraph(escaped or " ", style)


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
    ext = ".jpg" if "jpeg" in header or "jpg" in header else ".png"
    target = IMG_DIR / f"embedded_{index:03d}{ext}"
    target.write_bytes(base64.b64decode(payload))
    return target


def fit_image(path: Path, max_width: float) -> Image:
    image = Image(str(path))
    if image.drawWidth > max_width:
        ratio = max_width / image.drawWidth
        image.drawWidth *= ratio
        image.drawHeight *= ratio
    return image


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont(FONT, 8)
    canvas.setFillColor(colors.HexColor("#666666"))
    canvas.drawCentredString(A4[0] / 2, 10 * mm, f"第 {doc.page} 页")
    canvas.restoreState()


def build_pdf() -> dict:
    data = json.loads(DATA_PATH.read_text(encoding="utf-8"))
    chapters = data["chapters"]
    total_questions = sum(len(chapter["questions"]) for chapter in chapters)
    if len(chapters) != 6 or total_questions != 47:
        raise RuntimeError(f"Unexpected extraction size: {len(chapters)} chapters, {total_questions} questions")

    missing = [
        (chapter["chapter"], question.get("num"))
        for chapter in chapters
        for question in chapter["questions"]
        if not clean(question.get("stem")) or not clean(question.get("answer"))
    ]
    if missing:
        raise RuntimeError(f"Missing stem/answer: {missing}")

    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        "TitleCJK",
        parent=styles["Title"],
        fontName=FONT,
        fontSize=20,
        leading=28,
        alignment=TA_CENTER,
        spaceAfter=8 * mm,
    )
    chapter_style = ParagraphStyle(
        "Chapter",
        parent=styles["Heading1"],
        fontName=FONT,
        fontSize=15,
        leading=20,
        textColor=colors.HexColor("#1F2937"),
        spaceBefore=4 * mm,
        spaceAfter=3 * mm,
    )
    question_style = ParagraphStyle(
        "QuestionHead",
        parent=styles["Heading2"],
        fontName=FONT,
        fontSize=11.5,
        leading=16,
        textColor=colors.HexColor("#111827"),
        spaceAfter=2 * mm,
    )
    body_style = ParagraphStyle(
        "BodyCJK",
        parent=styles["BodyText"],
        fontName=FONT,
        fontSize=9.6,
        leading=15,
        spaceAfter=1.8 * mm,
    )
    answer_style = ParagraphStyle(
        "Answer",
        parent=body_style,
        textColor=colors.HexColor("#047857"),
        fontName=FONT,
    )
    small_style = ParagraphStyle(
        "Small",
        parent=body_style,
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
        paragraph("信息论基础六章作业题干与正确答案", title_style),
        paragraph("范围：仅包含选择题与填空题；每题均包含题干和正确答案。", small_style),
        paragraph(f"共 6 章，{total_questions} 道题。生成日期：2026-07-04。", small_style),
        Spacer(1, 4 * mm),
    ]

    image_index = 0
    embedded_images = 0
    for chapter_index, chapter in enumerate(chapters):
        if chapter_index:
            story.append(PageBreak())
        story.append(paragraph(chapter["chapter"], chapter_style))

        for question in chapter["questions"]:
            block = [
                paragraph(f"{question['num']}. {question['type']}", question_style),
                paragraph(f"题干：{display_stem(question)}", body_style),
            ]

            options = [option for option in question.get("options", []) if clean(option)]
            if options:
                block.append(paragraph("选项：", body_style))
                for option in options:
                    block.append(paragraph(option, body_style))

            images = question.get("images") or []
            if images:
                block.append(paragraph("题目图片/公式：", small_style))
                for image_info in images:
                    image_index += 1
                    if image_info.get("dataUrl"):
                        image_path = image_from_data_url(image_info["dataUrl"], image_index)
                    else:
                        image_path = download_image(image_info["src"], image_index)
                    embedded_images += 1
                    block.append(fit_image(image_path, doc.width - 10 * mm))
                    block.append(Spacer(1, 1.5 * mm))

            block.append(paragraph(f"正确答案：{question['answer']}", answer_style))
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
    return {"pdf": str(OUT_PATH), "questions": total_questions, "images": embedded_images}


if __name__ == "__main__":
    print(json.dumps(build_pdf(), ensure_ascii=False, indent=2))
