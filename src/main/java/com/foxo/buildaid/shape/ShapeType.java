package com.foxo.buildaid.shape;

import java.util.Locale;

/**
 * As formas que o guia sabe desenhar.
 *
 * <p>Gravadas na config pelo <b>nome</b>, nao pelo indice, para o JSON sobreviver a mudancas na
 * ordem do enum.
 */
public enum ShapeType {
	BOX,
	CYLINDER,
	SPHERE,
	DOME,
	PYRAMID,
	CONE,
	ROOF_GABLE,
	ROOF_HIP,
	HELIX,
	SPIRAL_STAIR,
	ARCH,
	TORUS,
	DIAMOND,
	BEZIER,
	OCTAGON,
	FENCE;

	public String translationKey() {
		return "buildaid.shape." + name().toLowerCase(Locale.ROOT);
	}

	public ShapeType next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static ShapeType parse(String name) {
		if (name == null) {
			return BOX;
		}
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return BOX;
		}
	}
}
